#!/usr/bin/env python3
"""Draw a symbolic open devotional manuscript, used for launcher icons.

No deity likeness or sourced sacred artwork is implied. Keep the significant
art in the adaptive icon's central 72/108 safe area.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
OUT = ROOT / 'design'
OUT.mkdir(exist_ok=True)
S = 4
BG = '#371c18'
GOLD = '#e9c77b'
PAPER = '#f4e1b2'
STROKE = '#a16538'

def xy(points):
    return [(round(x*S), round(y*S)) for x, y in points]

def render(size, adaptive=False):
    # A single master at 432px, scaled down with an antialiased resampler.
    im = Image.new('RGBA',(432*S,432*S), (0,0,0,0) if adaptive else BG)
    d = ImageDraw.Draw(im)
    # Adaptive icon's 108dp canvas has a 72dp guaranteed visible disc.
    # This mark stays in the central ~65dp of that canvas.
    if not adaptive:
        d.rounded_rectangle((15*S,15*S,417*S,417*S),radius=95*S,fill=BG)
    # Arched crown, a modest reference to an illuminated manuscript.
    d.arc((149*S,66*S,283*S,206*S), 188, 352, fill=GOLD, width=13*S)
    d.ellipse((205*S,87*S,227*S,109*S), fill=PAPER)
    # Book cover, shallow V, readable even at launcher size.
    d.polygon(xy([(92,180),(145,166),(203,180),(216,192),(229,180),
                  (287,166),(340,180),(340,288),(274,279),(216,301),
                  (158,279),(92,288)]), fill=GOLD)
    d.polygon(xy([(104,189),(151,178),(205,191),(210,200),(210,282),
                  (160,266),(104,276)]), fill=PAPER)
    d.polygon(xy([(328,189),(281,178),(227,191),(222,200),(222,282),
                  (272,266),(328,276)]), fill=PAPER)
    # Three ruled lines on each open leaf; no tiny text masquerading as content.
    for y, inset in [(218,0),(238,3),(258,6)]:
        d.line(xy([(122+inset,y),(190,y+4)]),fill=STROKE,width=5*S)
        d.line(xy([(310-inset,y),(242,y+4)]),fill=STROKE,width=5*S)
    d.line(xy([(216,194),(216,295)]),fill=STROKE,width=8*S)
    d.arc((88*S,258*S,344*S,330*S), 11, 169, fill=GOLD, width=8*S)
    return im.resize((size,size),Image.Resampling.LANCZOS)

# Android adaptive foreground is transparent and background is declared separately.
for density,px in [('mdpi',108),('hdpi',162),('xhdpi',216),('xxhdpi',324),('xxxhdpi',432)]:
    path=RES/('mipmap-'+density);path.mkdir(exist_ok=True)
    render(px,True).save(path/'ic_launcher_foreground.png')
for density,px in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    path=RES/('mipmap-'+density)
    render(px).save(path/'ic_launcher.png')
    render(px).save(path/'ic_launcher_round.png')
render(512).save(OUT/'launcher-icon-preview.png')
