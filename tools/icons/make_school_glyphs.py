"""Draws the glyphs of the Mage's school passives (textures/gui/skill/<school>_affinity.png): white 64x64 signs in the
style of the other skill glyphs, tinted by the skill's origin colour when drawn.

usage: python3 tools/icons/make_school_glyphs.py   (from the repository root)
"""
import json
import math
import os

from PIL import Image, ImageDraw

S = 256
OUT_DIR = os.path.join('src', 'main', 'resources', 'assets', 'solostatus', 'textures', 'gui', 'skill')


def ring(d):
    d.ellipse((14, 14, S - 14, S - 14), outline=255, width=14)


def teardrop(cx, cy, r, tip_y, lean):
    """A flame tongue: round bottom of radius r at (cx, cy), curving up to a tip leaning sideways."""
    pts = []
    for i in range(0, 181, 6):  # bottom half circle, left to right
        a = math.radians(180 - i)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    for t in [k / 12 for k in range(1, 13)]:  # right side up to the tip
        pts.append((cx + r * (1 - t) ** 1.4 + lean * t * t, cy - (cy - tip_y) * t))
    for t in [k / 12 for k in range(11, 0, -1)]:  # left side back down
        pts.append((cx - r * (1 - t) ** 0.9 + lean * t * t, cy - (cy - tip_y) * t))
    return pts


def flame(d):
    d.polygon(teardrop(128, 166, 58, 30, 26), fill=255)   # main tongue, tip bending right
    d.polygon(teardrop(84, 186, 34, 92, -30), fill=255)   # left tongue bending out
    d.polygon(teardrop(172, 186, 32, 104, 24), fill=255)  # right tongue
    d.polygon(teardrop(126, 194, 26, 126, -12), fill=0)   # hollow core


def snowflake(d):
    for k in range(6):
        a = math.radians(k * 60)
        x, y = 128 + 88 * math.cos(a), 128 + 88 * math.sin(a)
        d.line((128, 128, x, y), fill=255, width=16)
        for t in (0.55, 0.8):
            px, py = 128 + 88 * t * math.cos(a), 128 + 88 * t * math.sin(a)
            for s in (-1, 1):
                b = a + s * math.radians(45)
                d.line((px, py, px + 26 * math.cos(b), py + 26 * math.sin(b)), fill=255, width=12)


def lightning(d):
    d.polygon([(150, 40), (84, 140), (124, 140), (100, 216), (176, 108), (134, 108), (164, 40)], fill=255)


def ender(d):
    d.ellipse((48, 84, 208, 172), fill=255)
    d.ellipse((96, 92, 160, 164), fill=0)
    d.ellipse((114, 110, 142, 146), fill=255)


def evocation(d):
    for dx in (-44, 0, 44):
        h = 110 if dx == 0 else 80
        d.polygon([(128 + dx - 20, 196), (128 + dx, 196 - h), (128 + dx + 20, 196)], fill=255)
    d.rectangle((70, 196, 186, 210), fill=255)


def blood(d):
    d.polygon([(128, 40), (78, 140), (178, 140)], fill=255)
    d.ellipse((76, 100, 180, 204), fill=255)
    d.ellipse((104, 140, 128, 176), fill=0)


def eldritch(d):
    d.polygon([(40, 128), (90, 82), (166, 82), (216, 128), (166, 174), (90, 174)], fill=255)
    d.ellipse((98, 98, 158, 158), fill=0)
    d.rectangle((122, 104, 134, 152), fill=255)
    for k in range(4):
        x = 70 + k * 38
        d.line((x, 176, x - 8 + (k % 2) * 16, 224), fill=255, width=10)


GLYPHS = {'fire': flame, 'ice': snowflake, 'lightning': lightning, 'ender': ender, 'evocation': evocation,
          'blood': blood, 'eldritch': eldritch}


def main():
    for school, draw in GLYPHS.items():
        mask = Image.new('L', (S, S), 0)
        d = ImageDraw.Draw(mask)
        ring(d)
        inner = Image.new('L', (S, S), 0)
        draw(ImageDraw.Draw(inner))
        inner = inner.resize((int(S * 0.72), int(S * 0.72)), Image.LANCZOS)
        mask.paste(255, (int(S * 0.14), int(S * 0.14)), inner)
        img = Image.new('RGBA', (64, 64), (255, 255, 255, 0))
        img.putalpha(mask.resize((64, 64), Image.LANCZOS))
        name = school + '_affinity'
        img.save(os.path.join(OUT_DIR, name + '.png'))
        with open(os.path.join(OUT_DIR, name + '.png.mcmeta'), 'w') as f:
            json.dump({'texture': {'blur': True}}, f)
    print('glyphs:', len(GLYPHS))


if __name__ == '__main__':
    main()
