#!/usr/bin/env python3
"""Turn connected black JPEG backdrop into transparency, preserving figure RGB.

Only near-black components connected to image edges can become transparent;
interior dark pixels in the figure stay opaque. This avoids erasing shadows.
"""
from pathlib import Path
from PIL import Image
import numpy as np
from scipy import ndimage
root=Path(__file__).resolve().parents[1]/'design'
im=Image.open(root/'source/user-provided-removed-background-icon.jpg').convert('RGB')
a=np.asarray(im)
# JPEG edge antialias: background is (0,0,0) with slight compression noise.
near_black=a.max(axis=2)<50
labels,n=ndimage.label(near_black)
edge=np.unique(np.concatenate((labels[0,:],labels[-1,:],labels[:,0],labels[:,-1])))
back=np.isin(labels,edge)&near_black
# Connected near-black background, not isolated dark areas inside the figures.
# Distances softly ramp foreground's own anti-aliased edge for clean small icons.
distance=ndimage.distance_transform_edt(~back)
alpha=np.clip(distance*90,0,255).astype('uint8')
# The backdrop's near-black JPEG flecks are only at the edge; remove them too.
alpha[back]=0
rgba=np.dstack((a,alpha))
Image.fromarray(rgba,'RGBA').save(root/'owner-cutout-transparent.png')
print('transparent',int((alpha==0).sum()),'opaque',int((alpha==255).sum()),'partial',int(((alpha>0)&(alpha<255)).sum()))
