"""Textures, models, recipes and Epic Fight capabilities of the System's armour (SystemArmor.java).

usage: python3 tools/icons/make_armor.py   (from the repository root)

Armour layers are 64x32 in the player skin layout (layer 1: helmet, chestplate and arms, boots; layer 2:
leggings). Every face gets the set's base colour with a light top edge and a dark bottom edge, then trims and a
gem on the front faces. Item icons are 16x16 silhouettes in the same colours.
"""
import json
import os
import random

from PIL import Image, ImageDraw

RES = os.path.join('src', 'main', 'resources')
ARMOR_TEX = os.path.join(RES, 'assets', 'solostatus', 'textures', 'models', 'armor')
ITEM_TEX = os.path.join(RES, 'assets', 'solostatus', 'textures', 'item', 'armor')
MODELS = os.path.join(RES, 'assets', 'solostatus', 'models', 'item')
DATA = os.path.join(RES, 'data', 'solostatus')

SETS = {
    # base, light, dark, trim, gem
    'vanguard': ((150, 160, 176), (204, 212, 224), (82, 90, 104), (196, 154, 60), (232, 80, 58)),
    'stalker': ((54, 50, 62), (92, 86, 104), (28, 26, 34), (110, 84, 150), (155, 107, 255)),
    'arcanist': ((44, 70, 128), (86, 120, 190), (24, 38, 74), (220, 190, 96), (79, 168, 255)),
}

# (x, y, w, h) of the faces of each box in the 64x32 skin layout
HEAD = [(8, 0, 8, 8), (16, 0, 8, 8), (0, 8, 8, 8), (8, 8, 8, 8), (16, 8, 8, 8), (24, 8, 8, 8)]
BODY = [(20, 16, 8, 4), (28, 16, 8, 4), (16, 20, 4, 12), (20, 20, 8, 12), (28, 20, 4, 12), (32, 20, 8, 12)]
ARM = [(44, 16, 4, 4), (48, 16, 4, 4), (40, 20, 4, 12), (44, 20, 4, 12), (48, 20, 4, 12), (52, 20, 4, 12)]
LEG = [(4, 16, 4, 4), (8, 16, 4, 4), (0, 20, 4, 12), (4, 20, 4, 12), (8, 20, 4, 12), (12, 20, 4, 12)]


def shade(img, faces, colours, rnd, lower_only=False):
    base, light, dark = colours[:3]
    px = img.load()
    for (x, y, w, h) in faces:
        for yy in range(y, y + h):
            if lower_only and yy < 20 + 6 and h == 12:
                continue
            for xx in range(x, x + w):
                k = rnd.uniform(-10, 10)
                c = base
                if yy == y:
                    c = light
                elif yy == y + h - 1:
                    c = dark
                px[xx, yy] = tuple(max(0, min(255, int(v + k))) for v in c) + (255,)


def layer1(colours, rnd):
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    shade(img, HEAD, colours, rnd)
    shade(img, BODY, colours, rnd)
    shade(img, ARM, colours, rnd)
    shade(img, LEG, colours, rnd, lower_only=True)  # boots: the lower half of the legs
    d = ImageDraw.Draw(img)
    trim, gem = colours[3], colours[4]
    d.line([(8, 8), (15, 8)], fill=trim + (255,))       # helmet brow
    d.rectangle((11, 11, 12, 15), fill=(16, 16, 24, 255))  # visor slit
    d.rectangle((23, 20, 24, 31), fill=trim + (255,))     # chest centre seam
    d.rectangle((22, 23, 25, 26), fill=gem + (255,))      # chest gem
    d.line([(20, 30), (27, 30)], fill=trim + (255,))      # belt
    for x0 in (44, 40):
        d.line([(x0, 20), (x0 + 3, 20)], fill=trim + (255,))  # shoulder edge
    d.line([(4, 26), (7, 26)], fill=trim + (255,))        # boot cuff
    return img


def layer2(colours, rnd):
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    shade(img, BODY, colours, rnd)
    shade(img, LEG, colours, rnd)
    d = ImageDraw.Draw(img)
    d.line([(20, 20), (27, 20)], fill=colours[3] + (255,))  # waistband
    d.line([(5, 20), (5, 31)], fill=colours[2] + (255,))    # leg seam
    return img


ICONS = {  # 16x16 silhouettes: rows of 'b' base, 'l' light, 'd' dark, 't' trim, 'g' gem, 'o' outline
    'helmet': ["................", "................", "....oooooooo....", "...ollllllllo...", "..olbbbbbbbbdo..",
               "..obbbttttbbdo..", "..obbbbbbbbbdo..", "..obboooooobdo..", "..obo......obo..", "..odo......odo..",
               "...o........o...", "................", "................", "................", "................", "................"],
    'chestplate': ["................", "..ooo......ooo..", ".ollbo....oblbo.", ".obbbooooooobbdo", ".obbblllllbbbdo.",
                   "..oobbbtbbbbdoo.", "....obbtbbbdo...", "....obbggbbdo...", "....obbtbbbdo...", "....obbtbbbdo...",
                   "....ottttttto...", "....obbbbbbdo...", "....obbbbbbdo...", "....oooooooo....", "................", "................"],
    'leggings': ["................", "...oooooooooo...", "...otttttttto...", "...obbbbbbbbdo..", "...obbbbbbbbdo..",
                 "...obbbo.obbdo..", "...obbbo.obbdo..", "...obbbo.obbdo..", "...olbbo.olbdo..", "...obbbo.obbdo..",
                 "...obbbo.obbdo..", "...obbdo.obbdo..", "...oooo..oooo...", "................", "................", "................"],
    'boots': ["................", "................", "................", "................", "................",
              "...oooo..oooo...", "...olbo..olbo...", "...obbo..obbo...", "...obbo..obbo...", "...otto..otto...",
              "..obbbo..obbbo..", ".obbbdo..obbbdo.", ".oooooo..oooooo.", "................", "................", "................"],
}


def icon(rows, colours):
    base, light, dark, trim, gem = colours
    pal = {'b': base, 'l': light, 'd': dark, 't': trim, 'g': gem, 'o': (18, 16, 24)}
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in pal:
                img.putpixel((x, y), pal[c] + (255,))
    return img


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


PATTERNS = {'helmet': ['XXX', 'X X'], 'chestplate': ['X X', 'XGX', 'XXX'], 'leggings': ['XXX', 'X X', 'X X'], 'boots': ['X X', 'X X']}
MATERIALS = {'vanguard': 'minecraft:iron_ingot', 'stalker': 'minecraft:leather', 'arcanist': 'minecraft:purple_wool'}


def main():
    os.makedirs(ARMOR_TEX, exist_ok=True)
    os.makedirs(ITEM_TEX, exist_ok=True)
    for name, colours in SETS.items():
        rnd = random.Random(name)
        layer1(colours, rnd).save(os.path.join(ARMOR_TEX, name + '_layer_1.png'))
        layer2(colours, rnd).save(os.path.join(ARMOR_TEX, name + '_layer_2.png'))
        for piece, rows in ICONS.items():
            item = f'{name}_{piece}'
            icon(rows, colours).save(os.path.join(ITEM_TEX, item + '.png'))
            write_json(os.path.join(MODELS, item + '.json'),
                       {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'solostatus:item/armor/{item}'}})
            key = {'X': {'item': MATERIALS[name]}}
            pattern = PATTERNS[piece]
            if 'G' in ''.join(pattern):
                key['G'] = {'item': 'minecraft:amethyst_shard'}
            write_json(os.path.join(DATA, 'recipe', item + '.json'), {
                'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': pattern, 'key': key,
                'result': {'id': f'solostatus:{item}', 'count': 1}})
    print('armour sets:', len(SETS))


if __name__ == '__main__':
    main()
