"""Draws the System's own icons (weapon types, classes, magic) as 64x64 item textures and their item models.

usage: python3 tools/icons/make_icons.py            (run from the repository root)

Every icon is drawn as a vector mask at 256x256, scaled down to 64x64 with Lanczos, coloured with a soft vertical
gradient and given a dark outline so it reads on light and dark backgrounds. The icons are shown through the hidden
item solostatus:system_icon, one custom_model_data number per icon (see SystemIcon.java: the order of ICONS below
must match it).
"""
import json
import math
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

S = 256  # drawing size
OUT = 64  # texture size
ROOT = os.path.join('src', 'main', 'resources', 'assets', 'solostatus')
TEX_DIR = os.path.join(ROOT, 'textures', 'item', 'icon')
MODEL_DIR = os.path.join(ROOT, 'models', 'item')

WEAPON = (214, 244, 255)
# class colours from HunterClass
CLASS = {'warrior': (232, 80, 58), 'thief': (155, 107, 255), 'mage': (79, 168, 255),
         'defender': (201, 201, 201), 'archer': (99, 212, 107), 'support': (255, 224, 112)}


# ---------------------------------------------------------------- drawing helpers

class Pen:
    """Draws in a local frame: x along the weapon from handle (-) to tip (+), y across; rotated by angle degrees."""

    def __init__(self, mask, angle=-45.0, cx=S / 2, cy=S / 2, scale=0.62, dx=0.0):
        self.d = ImageDraw.Draw(mask)
        self.a = math.radians(angle)
        self.cx, self.cy, self.k, self.dx = cx, cy, scale, dx

    def p(self, x, y):
        x = (x + self.dx) * self.k
        y = y * self.k
        c, s = math.cos(self.a), math.sin(self.a)
        return (self.cx + x * c - y * s, self.cy + x * s + y * c)

    def poly(self, pts, fill=255):
        self.d.polygon([self.p(x, y) for x, y in pts], fill=fill)

    def rect(self, x0, y0, x1, y1, fill=255):
        self.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], fill)

    def circle(self, x, y, r, fill=255, width=0):
        px, py = self.p(x, y)
        r *= self.k
        if width:
            self.d.ellipse((px - r, py - r, px + r, py + r), outline=fill, width=max(1, int(width * self.k)))
        else:
            self.d.ellipse((px - r, py - r, px + r, py + r), fill=fill)

    def line(self, pts, width, fill=255):
        self.d.line([self.p(x, y) for x, y in pts], fill=fill, width=max(1, int(width * self.k)), joint='curve')
        for x, y in (pts[0], pts[-1]):  # round caps
            self.circle(x, y, width / 2, fill)


def new_mask():
    return Image.new('L', (S, S), 0)


# ---------------------------------------------------------------- weapons

def sword(pen):
    pen.poly([(-20, -12), (112, -12), (140, 0), (112, 12), (-20, 12)])
    pen.rect(-20, -2, 100, 2, 0)  # fuller
    pen.poly([(-34, -44), (-20, -40), (-20, 40), (-34, 44)])
    pen.rect(-90, -8, -34, 8)
    pen.circle(-100, 0, 14)


def greatsword(pen):
    pen.poly([(-12, -19), (122, -19), (156, 0), (122, 19), (-12, 19)])
    pen.rect(-4, -3, 112, 3, 0)
    pen.poly([(-28, -62), (-12, -50), (-12, 50), (-28, 62), (-20, 0)])
    pen.rect(-118, -9, -24, 9)
    pen.rect(-96, -11, -88, 11)  # grip wrap
    pen.rect(-70, -11, -62, 11)
    pen.circle(-128, 0, 17)


def dagger(pen):
    pen.poly([(-4, -15), (62, -12), (100, 0), (62, 12), (-4, 15)])
    pen.poly([(-18, -30), (-4, -26), (-4, 26), (-18, 30)])
    pen.rect(-66, -8, -18, 8)
    pen.circle(-76, 0, 12)


def axe(pen):
    pen.line([(-140, 0), (118, 0)], 16)
    pen.poly([(62, -8), (104, -8), (120, -40), (132, -92), (96, -78), (58, -92), (70, -40)])
    pen.poly([(74, 8), (98, 8), (88, 34)])


def mace(pen):
    pen.line([(-140, 0), (58, 0)], 16)
    pen.poly([(56, -58), (126, -58), (138, -46), (138, 46), (126, 58), (56, 58), (46, 46), (46, -46)])
    pen.rect(70, -58, 80, 58, 0)  # bands on the head
    pen.rect(102, -58, 112, 58, 0)
    pen.rect(-60, -11, -52, 11)


def spear(pen):
    pen.line([(-150, 0), (88, 0)], 12)
    pen.rect(72, -11, 86, 11)
    pen.poly([(86, -7), (98, -24), (158, 0), (98, 24), (86, 7)])


def bow(pen):
    pts = [(x, -18 - 70 * (1 - (x / 132) ** 2)) for x in range(-132, 133, 6)]
    pen.line(pts, 16)
    pen.line([(-132, -18), (132, -18)], 4)
    pen.rect(-14, -98, 14, -78)


def crossbow(pen):
    pen.rect(-128, -12, 40, 12)
    pen.poly([(-128, -12), (-150, -26), (-150, 26), (-128, 12)])
    arc = [(54 + 26 * (1 - (y / 104) ** 2), y) for y in range(-104, 105, 8)]
    pen.line(arc, 15)
    pen.line([(54, -104), (6, 0), (54, 104)], 4)
    pen.line([(6, 0), (130, 0)], 6)
    pen.poly([(126, -12), (156, 0), (126, 12)])


def staff(pen):
    pen.line([(-150, 0), (78, 0)], 12)
    pen.circle(118, 0, 40, width=12)
    pen.circle(118, 0, 18)
    pen.poly([(74, -10), (84, -32), (96, -10)])
    pen.poly([(74, 10), (84, 32), (96, 10)])


def shield(pen):
    pen.poly([(-96, -86), (96, -86), (96, 0), (0, 104), (-96, 0)])
    pen.poly([(-74, -66), (74, -66), (74, -6), (0, 76), (-74, -6)], 0)
    pen.poly([(-60, -54), (60, -54), (60, -10), (0, 56), (-60, -10)])
    pen.rect(-8, -54, 8, 50, 0)
    pen.rect(-60, -26, 60, -12, 0)


def magic(pen):
    pen.circle(0, 0, 112, width=12)
    pen.circle(0, 0, 80, width=6)
    star = []
    for i in range(8):
        r = 72 if i % 2 == 0 else 20
        a = math.radians(i * 45 - 90)
        star.append((r * math.cos(a), r * math.sin(a)))
    pen.poly(star)
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        pen.circle(96 * math.cos(a), 96 * math.sin(a), 7)


# ---------------------------------------------------------------- classes

def warrior(mask):
    sword(Pen(mask, -45, scale=0.56, dx=8))
    sword(Pen(mask, -135, scale=0.56, dx=8))


def thief(mask):
    p = Pen(mask, 0, scale=0.62)
    # hood
    p.poly([(-96, 104), (-92, 10), (-62, -62), (0, -112), (62, -62), (92, 10), (96, 104)])
    p.poly([(-62, 100), (-58, 8), (-36, -40), (0, -66), (36, -40), (58, 8), (62, 100)], 0)
    # eyes in the shadow
    p.poly([(-40, 8), (-10, 4), (-14, 18), (-36, 20)])
    p.poly([(40, 8), (10, 4), (14, 18), (36, 20)])
    # dagger across the chest
    dagger(Pen(mask, -30, cy=S * 0.74, scale=0.36))


def mage(mask):
    p = Pen(mask, 0, scale=0.62)
    p.poly([(-128, 92), (128, 92), (100, 70), (40, 62), (18, -40), (64, -112), (-6, -66), (-40, 62), (-100, 70)])
    p.rect(-46, 46, 46, 60, 0)
    star = []
    for i in range(10):
        r = 22 if i % 2 == 0 else 9
        a = math.radians(i * 36 - 90)
        star.append((4 + r * math.cos(a), 12 + r * math.sin(a)))
    p.poly(star, 0)


def defender(mask):
    p = Pen(mask, 0, scale=0.62)
    p.circle(0, 0, 118)
    p.circle(0, 0, 100, 0)
    p.circle(0, 0, 84)
    p.circle(0, 0, 36, 0)
    p.circle(0, 0, 22)
    for i in range(8):
        a = math.radians(i * 45)
        p.circle(62 * math.cos(a), 62 * math.sin(a), 10, 0)


def archer(mask):
    bow(Pen(mask, -45, scale=0.6))
    p = Pen(mask, -45, scale=0.6)
    p.line([(-120, 26), (118, 26)], 7)
    p.poly([(112, 12), (150, 26), (112, 40)])
    p.poly([(-120, 26), (-142, 8), (-130, 26), (-142, 44)])


def support(mask):
    p = Pen(mask, 0, scale=0.62)
    # heart
    p.circle(-50, -40, 58)
    p.circle(50, -40, 58)
    p.poly([(-104, -22), (104, -22), (0, 112)])
    # cross cut out
    p.rect(-12, -64, 12, 34, 0)
    p.rect(-44, -28, 44, -4, 0)


WEAPONS = {'sword': sword, 'greatsword': greatsword, 'dagger': dagger, 'axe': axe, 'mace': mace, 'spear': spear,
           'bow': bow, 'crossbow': crossbow, 'staff': staff, 'shield': shield}
CLASSES = {'warrior': warrior, 'thief': thief, 'mage': mage, 'defender': defender, 'archer': archer,
           'support': support}

# custom_model_data = index + 1; keep in step with SystemIcon.java
ICONS = ['weapon_' + k for k in WEAPONS] + ['magic'] + ['class_' + k for k in CLASSES]


def draw(name):
    m = new_mask()
    if name.startswith('weapon_'):
        kind = name[7:]
        WEAPONS[kind](Pen(m, 0, scale=0.66) if kind == 'shield' else Pen(m, scale=0.7))
        return m, WEAPON
    if name == 'magic':
        magic(Pen(m, 0, scale=0.62))
        return m, (175, 140, 255)
    CLASSES[name[6:]](m)
    return m, CLASS[name[6:]]


def render(mask, rgb, bold):
    # thin weapon strokes are made bolder so they still read in a 16x16 slot; filled class emblems keep their gaps
    mask = mask.filter(ImageFilter.MaxFilter(9 if bold else 3))
    small = mask.resize((OUT, OUT), Image.LANCZOS)
    outline = mask.filter(ImageFilter.MaxFilter(17)).resize((OUT, OUT), Image.LANCZOS)
    img = Image.new('RGBA', (OUT, OUT), (0, 0, 0, 0))
    dark = Image.new('RGBA', (OUT, OUT), (8, 16, 30, 0))
    dark.putalpha(outline.point(lambda v: int(v * 0.8)))
    img.alpha_composite(dark)
    fill = Image.new('RGBA', (OUT, OUT))
    px = fill.load()
    for y in range(OUT):
        t = y / (OUT - 1)
        light = 1.12 - 0.3 * t  # lighter at the top
        c = tuple(min(255, int(v * light + 30 * (1 - t))) for v in rgb)
        for x in range(OUT):
            px[x, y] = c + (255,)
    fill.putalpha(small)
    img.alpha_composite(fill)
    return img


def main():
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(os.path.join(MODEL_DIR, 'icon'), exist_ok=True)
    overrides = []
    for i, name in enumerate(ICONS):
        mask, rgb = draw(name)
        render(mask, rgb, name.startswith('weapon_')).save(os.path.join(TEX_DIR, name + '.png'))
        with open(os.path.join(MODEL_DIR, 'icon', name + '.json'), 'w') as f:
            json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'solostatus:item/icon/' + name}}, f, indent=2)
            f.write('\n')
        overrides.append({'predicate': {'custom_model_data': i + 1}, 'model': 'solostatus:item/icon/' + name})
    with open(os.path.join(MODEL_DIR, 'system_icon.json'), 'w') as f:
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'solostatus:item/icon/magic'},
                   'overrides': overrides}, f, indent=2)
        f.write('\n')
    print('icons:', len(ICONS))


if __name__ == '__main__':
    main()
