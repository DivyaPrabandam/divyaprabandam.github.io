#!/usr/bin/env python3
"""One launcher icon from the owner's transparent PNG, scaled for adaptive masks."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'app/src/main/res'
OUT=ROOT/'design'
SOURCE=OUT/'source/user-provided-transparent-icon.png'
MASTER=864
image=Image.open(SOURCE).convert('RGBA')
assert image.size==(640,640), 'Recheck source artwork before regenerating'
assert image.getpixel((0,0))[3]==0, 'Owner artwork should retain real transparency'
# The outermost nontransparent points of the figure fit a minimum enclosing
# circle of diameter ~661 source pixels, centered at (291.5,304.5). Scale that
# circle to 98% of the adaptive 72/108dp safe disc (576px on this canvas).
# This maximizes figure size without dropping the flag tip, staff end or feet.
scale=(576*.98)/661.3
sized=image.resize((round(640*scale),round(640*scale)),Image.Resampling.LANCZOS)
center=(291.5,304.5)
canvas=Image.new('RGBA',(MASTER,MASTER),(0,0,0,0))
x=round(MASTER/2-center[0]*scale)
y=round(MASTER/2-center[1]*scale)
canvas.alpha_composite(sized,(x,y))

def circle(im):
    mask=Image.new('L',im.size)
    ImageDraw.Draw(mask).ellipse((0,0,im.width-1,im.height-1),fill=255)
    out=im.copy();out.putalpha(Image.composite(im.getchannel('A'),Image.new('L',im.size),mask))
    return out

for density,px in [('mdpi',108),('hdpi',162),('xhdpi',216),('xxhdpi',324),('xxxhdpi',432)]:
    directory=RES/('mipmap-'+density);directory.mkdir(exist_ok=True)
    canvas.resize((px,px),Image.Resampling.LANCZOS).save(directory/'ic_launcher_foreground.png')
for density,px in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    directory=RES/('mipmap-'+density)
    icon=canvas.resize((px,px),Image.Resampling.LANCZOS)
    icon.save(directory/'ic_launcher.png')
    circle(icon).save(directory/'ic_launcher_round.png')
canvas.resize((512,512),Image.Resampling.LANCZOS).save(OUT/'launcher-icon-preview.png')
# The adaptive mask shows the central 72/108dp disc at launcher resolution.
safe=canvas.crop((144,144,720,720)).resize((48,48),Image.Resampling.LANCZOS)
circle(safe).save(OUT/'launcher-adaptive-48px-preview.png')
