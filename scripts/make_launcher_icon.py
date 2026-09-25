#!/usr/bin/env python3
"""Build two Android launchers from the user's unchanged artwork.

A: full painting with legacy rounded corners. B: masked original RGB pixels
for the figure and deity on transparent foreground. No generated painting.
"""
from pathlib import Path
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'app/src/main/res'
OUT=ROOT/'design'
SOURCE=OUT/'source/user-provided-icon.jpg'
CUTOUT=OUT/'figure-cutout.png'
MASTER=864
image=Image.open(SOURCE).convert('RGB')
assert image.size==(640,640), 'Recheck source artwork before regenerating'
full=image.resize((MASTER,MASTER),Image.Resampling.LANCZOS)
cut=Image.open(CUTOUT).convert('RGBA')
assert cut.size==(640,640)
bounds=cut.getchannel('A').getbbox()
assert bounds is not None
# Center the complete cutout including staff, pennant and feet in the adaptive
# 72/108dp guaranteed visible disc. No painting pixels are recolored.
figure=cut.crop(bounds)
max_extent=MASTER*0.63
ratio=min(max_extent/figure.width,max_extent/figure.height)
figure=figure.resize((round(figure.width*ratio),round(figure.height*ratio)),Image.Resampling.LANCZOS)
foreground=Image.new('RGBA',(MASTER,MASTER),(0,0,0,0))
foreground.alpha_composite(figure,((MASTER-figure.width)//2,(MASTER-figure.height)//2))

def rounded(canvas,radius):
    mask=Image.new('L',canvas.size);ImageDraw.Draw(mask).rounded_rectangle((0,0,canvas.width-1,canvas.height-1),radius=radius,fill=255)
    out=canvas.copy().convert('RGBA');out.putalpha(mask);return out

def circle(canvas):
    mask=Image.new('L',canvas.size);ImageDraw.Draw(mask).ellipse((0,0,canvas.width-1,canvas.height-1),fill=255)
    out=canvas.copy().convert('RGBA');out.putalpha(mask);return out

for density,px in [('mdpi',108),('hdpi',162),('xhdpi',216),('xxhdpi',324),('xxxhdpi',432)]:
    path=RES/('mipmap-'+density);path.mkdir(exist_ok=True)
    full.resize((px,px),Image.Resampling.LANCZOS).save(path/'ic_launcher_foreground.png')
    foreground.resize((px,px),Image.Resampling.LANCZOS).save(path/'ic_launcher_cutout_foreground.png')
for density,px in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    path=RES/('mipmap-'+density)
    fullscaled=full.resize((px,px),Image.Resampling.LANCZOS)
    rounded(fullscaled,round(px*.17)).save(path/'ic_launcher.png')
    circle(fullscaled).save(path/'ic_launcher_round.png')
    figure_scaled=foreground.resize((px,px),Image.Resampling.LANCZOS)
    figure_scaled.save(path/'ic_launcher_cutout.png')
    figure_scaled.save(path/'ic_launcher_cutout_round.png')
rounded(full,round(MASTER*.17)).resize((512,512),Image.Resampling.LANCZOS).save(OUT/'launcher-icon-preview.png')
foreground.resize((512,512),Image.Resampling.LANCZOS).save(OUT/'launcher-cutout-preview.png')
# For actual round-launcher size show the full adaptive circle with the cutout
# inside it, not a falsely enlarged crop of the guaranteed-safe center.
small=foreground.resize((48,48),Image.Resampling.LANCZOS)
circle(small).save(OUT/'launcher-cutout-round-48px-preview.png')
