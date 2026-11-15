"""Textures, models, tags, recipes and Epic Fight capabilities of the System's weapons (SystemWeapons.java).

usage: python3 tools/icons/make_weapons.py   (from the repository root)

Each weapon is drawn in parts (blade, edge, fittings, grip, gem) as vector masks at 128x128, scaled to a 32x32
texture with a dark outline. Hunter grade is light steel with a cyan core; Shadow grade is violet-black steel with
a glowing edge.
"""
import json
import math
import os

from PIL import Image, ImageDraw, ImageFilter

S, OUT = 128, 32
RES = os.path.join('src', 'main', 'resources')
TEX = os.path.join(RES, 'assets', 'solostatus', 'textures', 'item', 'weapon')
MODELS = os.path.join(RES, 'assets', 'solostatus', 'models', 'item')
DATA = os.path.join(RES, 'data', 'solostatus')

PALETTE = {
    'hunter': {'blade': (196, 212, 226), 'edge': (246, 251, 255), 'shade': (128, 146, 166), 'fit': (70, 92, 124),
               'grip': (86, 58, 40), 'gem': (90, 224, 255), 'wood': (120, 84, 52)},
    'shadow': {'blade': (66, 52, 92), 'edge': (196, 132, 255), 'shade': (34, 26, 50), 'fit': (28, 22, 40),
               'grip': (44, 32, 56), 'gem': (214, 100, 255), 'wood': (52, 38, 64)},
}

# weapon kind -> (item tag type, Epic Fight weapon type)
KINDS = {'sword': ('sword', 'sword'), 'greatsword': ('greatsword', 'greatsword'), 'dagger': ('dagger', 'dagger'),
         'battleaxe': ('axe', 'axe'), 'warhammer': ('mace', 'greatsword'), 'spear': ('spear', 'spear'),
         'staff': ('staff', 'spear')}


class Pen:
    """Local frame: x along the weapon from the end of the grip (-) to the tip (+), y across; turned -45°."""

    def __init__(self, scale=0.36):
        self.k = scale
        self.layers = {}

    def p(self, x, y):
        a = math.radians(-45)
        x, y = x * self.k, y * self.k
        return (S / 2 + x * math.cos(a) - y * math.sin(a), S / 2 + x * math.sin(a) + y * math.cos(a))

    def layer(self, name):
        if name not in self.layers:
            self.layers[name] = Image.new('L', (S, S), 0)
        return ImageDraw.Draw(self.layers[name])

    def poly(self, name, pts):
        self.layer(name).polygon([self.p(x, y) for x, y in pts], fill=255)

    def rect(self, name, x0, y0, x1, y1):
        self.poly(name, [(x0, y0), (x1, y0), (x1, y1), (x0, y1)])

    def circle(self, name, x, y, r):
        px, py = self.p(x, y)
        r *= self.k
        self.layer(name).ellipse((px - r, py - r, px + r, py + r), fill=255)


def sword(p):
    p.poly('blade', [(-20, -15), (118, -15), (150, 0), (118, 15), (-20, 15)])
    p.poly('edge', [(-20, -15), (118, -15), (150, 0), (118, -9), (-20, -9)])
    p.rect('shade', -10, -2, 104, 2)
    p.poly('fit', [(-36, -46), (-20, -42), (-20, 42), (-36, 46)])
    p.rect('grip', -94, -9, -36, 9)
    p.circle('gem', -104, 0, 14)


def greatsword(p):
    p.poly('blade', [(-14, -24), (128, -24), (162, 0), (128, 24), (-14, 24)])
    p.poly('edge', [(-14, -24), (128, -24), (162, 0), (128, -16), (-14, -16)])
    p.rect('shade', -4, -4, 116, 4)
    p.poly('fit', [(-32, -66), (-14, -54), (-14, 54), (-32, 66), (-24, 0)])
    p.circle('gem', -22, 0, 11)
    p.rect('grip', -122, -10, -32, 10)
    p.circle('fit', -132, 0, 16)


def dagger(p):
    p.poly('blade', [(-4, -17), (68, -14), (108, 0), (68, 14), (-4, 17)])
    p.poly('edge', [(-4, -17), (68, -14), (108, 0), (68, -8), (-4, -10)])
    p.poly('fit', [(-20, -34), (-4, -28), (-4, 28), (-20, 34)])
    p.rect('grip', -70, -9, -20, 9)
    p.circle('gem', -80, 0, 13)


def battleaxe(p):
    p.rect('wood', -150, -9, 120, 9)
    for side in (-1, 1):
        p.poly('blade', [(52, 8 * side), (100, 8 * side), (118, 44 * side), (126, 96 * side), (86, 82 * side), (44, 96 * side), (56, 44 * side)])
        p.poly('edge', [(118, 44 * side), (126, 96 * side), (86, 82 * side), (44, 96 * side), (48, 84 * side), (86, 72 * side), (114, 84 * side)])
    p.rect('fit', 60, -12, 96, 12)
    p.circle('gem', 78, 0, 10)
    p.rect('fit', -60, -12, -48, 12)


def warhammer(p):
    p.rect('wood', -150, -9, 60, 9)
    p.poly('blade', [(52, -64), (130, -64), (142, -50), (142, 50), (130, 64), (52, 64), (40, 50), (40, -50)])
    p.poly('edge', [(130, -64), (142, -50), (142, 50), (130, 64), (122, 64), (132, 50), (132, -50), (122, -64)])
    p.rect('fit', 70, -64, 80, 64)
    p.rect('fit', 104, -64, 114, 64)
    p.circle('gem', 92, 0, 14)
    p.rect('fit', -64, -12, -52, 12)


def spear(p):
    p.rect('wood', -158, -7, 96, 7)
    p.rect('fit', 78, -12, 96, 12)
    p.circle('gem', 87, 0, 8)
    p.poly('blade', [(96, -8), (108, -28), (168, 0), (108, 28), (96, 8)])
    p.poly('edge', [(96, -8), (108, -28), (168, 0), (108, -14)])
    p.rect('fit', -158, -9, -146, 9)


def staff(p):
    p.rect('wood', -158, -7, 90, 7)
    p.poly('fit', [(84, -12), (96, -12), (96, 12), (84, 12)])
    # a crescent cradle around the core
    for side in (-1, 1):
        p.poly('blade', [(92, 10 * side), (120, 44 * side), (150, 40 * side), (134, 28 * side), (112, 12 * side)])
    p.circle('gem', 126, 0, 24)
    p.circle('edge', 120, -6, 8)
    p.rect('fit', -60, -10, -48, 10)


DRAW = {'sword': sword, 'greatsword': greatsword, 'dagger': dagger, 'battleaxe': battleaxe,
        'warhammer': warhammer, 'spear': spear, 'staff': staff}
ORDER = ['wood', 'grip', 'fit', 'blade', 'shade', 'edge', 'gem']


def render(kind, grade):
    pen = Pen()
    DRAW[kind](pen)
    pal = PALETTE[grade]
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    full = Image.new('L', (S, S), 0)
    for name in ORDER:
        if name not in pen.layers:
            continue
        mask = pen.layers[name]
        full.paste(255, (0, 0), mask)
        colour = Image.new('RGBA', (S, S), pal[name] + (255,))
        img.paste(colour, (0, 0), mask)
    if grade == 'shadow':  # a faint violet glow around the blade
        glow = pen.layers.get('edge', Image.new('L', (S, S))).filter(ImageFilter.GaussianBlur(3)).point(lambda v: int(v * 0.5))
        g = Image.new('RGBA', (S, S), pal['edge'] + (0,))
        g.putalpha(glow)
        img = Image.alpha_composite(g, img)
        full = Image.composite(Image.new('L', (S, S), 255), full, glow.point(lambda v: 255 if v > 40 else 0))
    small = img.resize((OUT, OUT), Image.LANCZOS)
    alpha = small.getchannel('A').point(lambda v: 255 if v > 90 else 0)
    small.putalpha(alpha)
    outline = alpha.filter(ImageFilter.MaxFilter(3))
    out = Image.new('RGBA', (OUT, OUT), (16, 12, 24, 0))
    out.putalpha(outline)
    out.alpha_composite(small)
    return out


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def main():
    os.makedirs(TEX, exist_ok=True)
    tags = {}
    for grade in PALETTE:
        for kind, (tag, ef_type) in KINDS.items():
            item = f'{grade}_{kind}'
            render(kind, grade).save(os.path.join(TEX, item + '.png'))
            write_json(os.path.join(MODELS, item + '.json'),
                       {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'solostatus:item/weapon/{item}'}})
            tags.setdefault(tag, []).append(f'solostatus:{item}')
            # Epic Fight: a moveset by weapon type (its own data format: capabilities/weapons/<item>.json)
            heavy = kind in ('greatsword', 'warhammer')
            write_json(os.path.join(DATA, 'capabilities', 'weapons', item + '.json'), {
                'attributes': {'common': {'armor_negation': (20.0 if grade == 'shadow' else 10.0) + (5 if heavy else 0),
                                          'impact': 3.5 if heavy else 1.2 if kind == 'dagger' else 2.0,
                                          'max_strikes': 3 if heavy else 1}},
                'type': f'epicfight:{ef_type}'})
    # recipes: Hunter grade is forged, Shadow grade is a netherite upgrade
    hunter_patterns = {
        'sword': [' I ', 'AIA', ' S '],
        'greatsword': [' II', 'III', 'SA '],
        'dagger': ['  I', ' I ', 'A  '],
        'battleaxe': ['III', 'IS ', ' S '],
        'warhammer': ['III', 'IAI', ' S '],
        'spear': ['  I', ' A ', 'S  '],
        'staff': [' AA', ' SA', 'S  '],
    }
    for kind, pattern in hunter_patterns.items():
        keys = {'I': {'item': 'minecraft:iron_ingot'}, 'S': {'item': 'minecraft:stick'}, 'A': {'item': 'minecraft:amethyst_shard'}}
        used = {c for row in pattern for c in row if c != ' '}
        write_json(os.path.join(DATA, 'recipe', f'hunter_{kind}.json'), {
            'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': pattern,
            'key': {c: keys[c] for c in sorted(used)}, 'result': {'id': f'solostatus:hunter_{kind}', 'count': 1}})
        write_json(os.path.join(DATA, 'recipe', f'shadow_{kind}_smithing.json'), {
            'type': 'minecraft:smithing_transform',
            'template': {'item': 'minecraft:netherite_upgrade_smithing_template'},
            'base': {'item': f'solostatus:hunter_{kind}'}, 'addition': {'item': 'minecraft:netherite_ingot'},
            'result': {'id': f'solostatus:shadow_{kind}', 'count': 1}})
    for tag, items in tags.items():  # merged into the existing tag: entries already there stay
        path = os.path.join(DATA, 'tags', 'item', 'weapons', tag + '.json')
        values = json.load(open(path))['values'] if os.path.exists(path) else []
        values += [i for i in items if i not in values]
        write_json(path, {'replace': False, 'values': values})
    print('weapons:', sum(len(v) for v in tags.values()))


if __name__ == '__main__':
    main()
