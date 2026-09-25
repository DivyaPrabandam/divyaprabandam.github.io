#!/usr/bin/env python3
"""Package the user's square artwork full-bleed as Android launcher icons.

The original image is scaled only. Launcher masks may crop its corners.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/'app/src/main/res'
OUT = ROOT/'design'
SOURCE = OUT/'source/user-provided-icon.jpg'
BACKGROUND = '#4b2818'
MASTER = 864

image = Image.open(SOURCE).convert('RGB')
assert image.size == (640,640), 'Recheck source artwork before regenerating'

master = image.resize((MASTER,MASTER),Image.Resampling.LANCZOS)
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
# Simulate a round adaptive launcher mask over the full-bleed foreground.
# The 72dp disc is the guaranteed visible zone of its 108dp canvas.
mask=Image.new('L',(MASTER,MASTER));ImageDraw.Draw(mask).ellipse((144,144,720,720),fill=255)
round_icon=master.copy().convert('RGBA');round_icon.putalpha(mask)
round_icon.crop((144,144,720,720)).resize((48,48),Image.Resampling.LANCZOS).save(OUT/'launcher-adaptive-48px-preview.png')
