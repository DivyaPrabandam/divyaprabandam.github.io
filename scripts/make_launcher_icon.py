#!/usr/bin/env python3
"""Build two launchers from the owner's supplied square images.

The alternate icon uses the owner's black-background JPEG with its connected
black backdrop keyed to alpha. The original RGB of the figures is retained.
"""
from pathlib import Path
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'app/src/main/res'
OUT=ROOT/'design'
MASTER=864

def source(filename):
    image=Image.open(OUT/'source'/filename).convert('RGB')
    assert image.size==(640,640), 'Recheck source artwork before regenerating'
    return image.resize((MASTER,MASTER),Image.Resampling.LANCZOS)

full=source('user-provided-icon.jpg')
cutout=Image.open(OUT/'owner-cutout-transparent.png').convert('RGBA')
assert cutout.size==(640,640)
black=cutout.resize((MASTER,MASTER),Image.Resampling.LANCZOS)

def rounded(canvas,radius):
    mask=Image.new('L',canvas.size);ImageDraw.Draw(mask).rounded_rectangle((0,0,canvas.width-1,canvas.height-1),radius=radius,fill=255)
    out=canvas.copy().convert('RGBA');out.putalpha(mask);return out

def circle(canvas):
    mask=Image.new('L',canvas.size);ImageDraw.Draw(mask).ellipse((0,0,canvas.width-1,canvas.height-1),fill=255)
    out=canvas.copy().convert('RGBA');out.putalpha(mask);return out

for density,px in [('mdpi',108),('hdpi',162),('xhdpi',216),('xxhdpi',324),('xxxhdpi',432)]:
    path=RES/('mipmap-'+density);path.mkdir(exist_ok=True)
    full.resize((px,px),Image.Resampling.LANCZOS).save(path/'ic_launcher_foreground.png')
    black.resize((px,px),Image.Resampling.LANCZOS).save(path/'ic_launcher_cutout_foreground.png')
for density,px in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    path=RES/('mipmap-'+density)
    rounded(full.resize((px,px),Image.Resampling.LANCZOS),round(px*.17)).save(path/'ic_launcher.png')
    circle(full.resize((px,px),Image.Resampling.LANCZOS)).save(path/'ic_launcher_round.png')
    cutscaled=black.resize((px,px),Image.Resampling.LANCZOS)
    cutscaled.save(path/'ic_launcher_cutout.png')
    circle(cutscaled).save(path/'ic_launcher_cutout_round.png')
rounded(full,round(MASTER*.17)).resize((512,512),Image.Resampling.LANCZOS).save(OUT/'launcher-icon-preview.png')
black.resize((512,512),Image.Resampling.LANCZOS).save(OUT/'launcher-cutout-preview.png')
circle(black.resize((48,48),Image.Resampling.LANCZOS)).save(OUT/'launcher-cutout-round-48px-preview.png')
