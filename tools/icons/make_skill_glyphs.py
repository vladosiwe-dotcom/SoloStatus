"""Glyphs of the third specializations' skills (textures/gui/skill/<id>.png): white 64x64 signs in the style of
the other skill glyphs, tinted by the skill's origin colour when drawn.

usage: python3 tools/icons/make_skill_glyphs.py   (from the repository root)
"""
import json
import math
import os

from PIL import Image, ImageDraw

S = 256
OUT_DIR = os.path.join('src', 'main', 'resources', 'assets', 'solostatus', 'textures', 'gui', 'skill')


def rot(points, angle, cx=128, cy=128):
    a = math.radians(angle)
    return [(cx + (x - cx) * math.cos(a) - (y - cy) * math.sin(a), cy + (x - cx) * math.sin(a) + (y - cy) * math.cos(a)) for x, y in points]


def blade(d, angle, length=150, width=16, cx=128, cy=128):
    """A sword pointing up, turned by angle around (cx, cy): blade, guard, grip, pommel."""
    top = cy - length / 2
    pts = [(cx - width / 2, cy + 20), (cx - width / 2, top + 24), (cx, top), (cx + width / 2, top + 24), (cx + width / 2, cy + 20)]
    d.polygon(rot(pts, angle, cx, cy), fill=255)
    d.polygon(rot([(cx - 34, cy + 20), (cx + 34, cy + 20), (cx + 34, cy + 32), (cx - 34, cy + 32)], angle, cx, cy), fill=255)
    d.polygon(rot([(cx - 7, cy + 32), (cx + 7, cy + 32), (cx + 7, cy + 66), (cx - 7, cy + 66)], angle, cx, cy), fill=255)
    px, py = rot([(cx, cy + 74)], angle, cx, cy)[0]
    d.ellipse((px - 11, py - 11, px + 11, py + 11), fill=255)


def star(d, cx, cy, r_out, r_in, n=4, angle=-90):
    pts = []
    for i in range(n * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.radians(angle + i * 180 / n)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d.polygon(pts, fill=255)


def drop(d, cx, cy, r, fill=255):
    d.polygon([(cx, cy - r * 2.1), (cx - r * 0.95, cy - r * 0.2), (cx + r * 0.95, cy - r * 0.2)], fill=fill)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill)


def note(d, x, y, s=1.0):
    d.ellipse((x - 26 * s, y - 18 * s, x + 18 * s, y + 18 * s), fill=255)
    d.rectangle((x + 8 * s, y - 120 * s, x + 18 * s, y), fill=255)
    d.polygon([(x + 18 * s, y - 120 * s), (x + 60 * s, y - 92 * s), (x + 60 * s, y - 72 * s), (x + 18 * s, y - 96 * s)], fill=255)


def duel_challenge(d):
    blade(d, -40, 170)
    blade(d, 40, 170)


def fencing(d):
    d.line([(70, 190), (206, 54)], fill=255, width=10)
    d.polygon([(200, 50), (214, 44), (208, 60)], fill=255)
    d.arc((50, 150, 110, 210), 200, 470, fill=255, width=10)
    d.line([(66, 194), (40, 220)], fill=255, width=16)


def toxic_cloud(d):
    for x, y, r in ((92, 112, 44), (140, 92, 54), (180, 120, 40), (130, 130, 46)):
        d.ellipse((x - r, y - r, x + r, y + r), fill=255)
    d.rectangle((60, 120, 210, 150), fill=255)
    for x in (88, 128, 168):
        drop(d, x, 205, 14)


def venom_mastery(d):
    blade(d, 35, 150, 18, 112, 118)
    drop(d, 190, 200, 20)


def arcane_strike(d):
    blade(d, 35, 170, 18, 120, 136)
    star(d, 184, 66, 46, 12)


def battle_magic(d):
    blade(d, -35, 170, 18, 136, 136)
    star(d, 68, 70, 30, 8)
    star(d, 196, 196, 24, 7)


def retribution(d):
    star(d, 128, 128, 112, 46, n=8, angle=-90)
    d.ellipse((92, 92, 164, 164), fill=0)
    d.ellipse((108, 108, 148, 148), fill=255)


def vengeance(d):
    d.polygon([(48, 40), (208, 40), (208, 120), (128, 226), (48, 120)], fill=255)
    d.polygon([(70, 60), (186, 60), (186, 116), (128, 196), (70, 116)], fill=0)
    d.polygon([(128, 80), (104, 140), (118, 134), (112, 172), (152, 120), (136, 124), (150, 80)], fill=255)


def bolt_barrage(d):
    for off in (-52, 0, 52):
        d.line([(56 + off, 200 + off * 0.2), (176 + off, 80 + off * 0.2)], fill=255, width=12)
        tip = (196 + off, 60 + off * 0.2)
        d.polygon([tip, (166 + off, 74 + off * 0.2), (182 + off, 90 + off * 0.2)], fill=255)


def auto_reload(d):
    d.arc((42, 42, 214, 214), 30, 320, fill=255, width=22)
    d.polygon([(206, 70), (226, 128), (168, 112)], fill=255)


def battle_hymn(d):
    note(d, 92, 196, 1.0)
    note(d, 168, 176, 0.85)


def melody_of_life(d):
    note(d, 84, 190, 1.0)
    d.ellipse((128, 100, 172, 144), fill=255)
    d.ellipse((164, 100, 208, 144), fill=255)
    d.polygon([(132, 132), (204, 132), (168, 184)], fill=255)


GLYPHS = {f.__name__: f for f in (duel_challenge, fencing, toxic_cloud, venom_mastery, arcane_strike, battle_magic,
                                   retribution, vengeance, bolt_barrage, auto_reload, battle_hymn, melody_of_life)}


def main():
    for name, draw in GLYPHS.items():
        mask = Image.new('L', (S, S), 0)
        draw(ImageDraw.Draw(mask))
        img = Image.new('RGBA', (64, 64), (255, 255, 255, 0))
        img.putalpha(mask.resize((64, 64), Image.LANCZOS))
        img.save(os.path.join(OUT_DIR, name + '.png'))
        with open(os.path.join(OUT_DIR, name + '.png.mcmeta'), 'w') as f:
            json.dump({'texture': {'blur': True}}, f)
    print('glyphs:', len(GLYPHS))


if __name__ == '__main__':
    main()
