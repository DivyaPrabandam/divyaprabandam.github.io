#!/usr/bin/env python3
"""Manual alpha-mat of the user's artwork; source RGB is left unchanged."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFilter
p=Path(__file__).resolve().parents[1]/'design'
source=Image.open(p/'source/user-provided-icon.jpg').convert('RGB')
scale=4
mat=Image.new('L',(640*scale,640*scale))
d=ImageDraw.Draw(mat)
def polygon(pts):d.polygon([(x*scale,y*scale) for x,y in pts],fill=255)
# Cut only the object contours. No pixel colour on the figures is synthesized.
# Cloth pennant with its attached flagpole.
polygon([(132,18),(145,19),(150,31),(156,54),(161,76),(169,102),(178,126),
 (172,139),(166,137),(160,126),(151,129),(143,126),(130,121),(120,106),
 (109,104),(105,99),(112,89),(118,76),(106,70),(105,65),(112,50),(120,43),(126,27)])
# Staff from the pennant to its complete bottom endpoint.
polygon([(145,82),(157,80),(178,151),(190,201),(211,267),(228,337),
 (245,415),(259,485),(272,547),(279,609),(266,617),(253,562),(242,502),
 (227,431),(211,365),(194,286),(177,218),(161,158)])
# Ramanujar, the deity, robes, seated feet and pedestal.
polygon([(227,105),(237,100),(247,108),(256,113),(262,130),(269,114),
 (271,102),(282,91),(291,81),(307,77),(318,77),(340,82),(352,92),
 (359,104),(367,125),(366,143),(373,158),(384,171),(413,182),
 (440,204),(453,219),(462,246),(466,269),(480,302),(488,329),
 (491,363),(486,388),(474,400),(479,415),(504,430),(518,442),
 (524,455),(526,465),(520,476),(505,489),(485,498),(482,516),
 (485,545),(484,560),(475,570),(458,573),(440,570),(425,577),
 (418,587),(405,591),(393,584),(376,562),(352,560),(324,563),
 (302,559),(278,556),(265,548),(244,546),(221,538),(200,531),
 (180,529),(162,525),(147,518),(136,504),(130,491),(126,476),
 (130,460),(140,449),(157,439),(173,429),(193,419),(198,398),
 (200,378),(202,348),(196,327),(191,313),(184,298),(180,283),
 (182,263),(187,247),(196,239),(204,239),(200,225),(190,221),
 (180,211),(173,195),(173,177),(180,167),(190,163),(201,159),
 (202,150),(208,131),(216,117)])
mat=mat.resize((640,640),Image.Resampling.LANCZOS).filter(ImageFilter.GaussianBlur(.6))
rgba=source.copy().convert('RGBA');rgba.putalpha(mat);rgba.save(p/'figure-cutout.png')
