#!/usr/bin/env python3
"""Package the user's supplied square artwork as Android launcher icons.

The photo is scaled only, with a flat warm border to keep the figures clear of
adaptive launcher masks. Do not redraw, filter or change the source painting.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/'app/src/main/res'
OUT = ROOT/'design'
SOURCE = OUT/'source/user-provided-icon.jpg'
BACKGROUND = '#4b2818'
MASTER = 864
# Image occupies 72% of the adaptive canvas. The figures and faces are inside
# the minimum central safe circle; corners of the original scene can be masked.
ART = 624

image = Image.open(SOURCE).convert('RGB')
assert image.size == (640,640), 'Recheck source artwork before regenerating'

def square():
    canvas = Image.new('RGB',(MASTER,MASTER),BACKGROUND)
    resized = image.resize((ART,ART),Image.Resampling.LANCZOS)
    canvas.paste(resized,((MASTER-ART)//2,(MASTER-ART)//2))
    return canvas

master = square()
for density,px in [('mdpi',108),('hdpi',162),('xhdpi',216),('xxhdpi',324),('xxxhdpi',432)]:
    path=RES/('mipmap-'+density);path.mkdir(exist_ok=True)
    master.resize((px,px),Image.Resampling.LANCZOS).save(path/'ic_launcher_foreground.png')
for density,px in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    path=RES/('mipmap-'+density)
    flat=master.resize((px,px),Image.Resampling.LANCZOS)
    flat.save(path/'ic_launcher.png')
    mask=Image.new('L',(px,px));ImageDraw.Draw(mask).ellipse((0,0,px-1,px-1),fill=255)
    flat.putalpha(mask)
    flat.save(path/'ic_launcher_round.png')
master.resize((512,512),Image.Resampling.LANCZOS).save(OUT/'launcher-icon-preview.png')
# Simulate an adaptive launcher circle on its 108dp canvas, then resize the
# safe 72dp visible area to 48px for a realistic density-size visual check.
mask=Image.new('L',(MASTER,MASTER));ImageDraw.Draw(mask).ellipse((144,144,720,720),fill=255)
round_icon=master.copy().convert('RGBA');round_icon.putalpha(mask)
round_icon.crop((144,144,720,720)).resize((48,48),Image.Resampling.LANCZOS).save(OUT/'launcher-adaptive-48px-preview.png')
