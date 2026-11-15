"""Pixel art of the ring and the amulet (16x16 item textures). Layer 0 is the metal; layer 1 is the gem in grey
shades, tinted with the rarity colour in game (ClientSetup.itemColors).

usage: python3 tools/icons/make_jewellery.py   (from the repository root)
"""
import os

from PIL import Image

OUT = os.path.join('src', 'main', 'resources', 'assets', 'solostatus', 'textures', 'item')

# metal shades: outline, dark, mid, light, shine
GOLD = {'o': (74, 46, 12), 'd': (150, 98, 26), 'm': (214, 158, 46), 'l': (246, 210, 104), 's': (255, 246, 200)}
# gem shades (grey, tinted in game): dark, mid, light, shine
GEM = {'D': (110, 110, 110), 'M': (175, 175, 175), 'L': (225, 225, 225), 'S': (255, 255, 255)}

RING = [
    "................",
    "................",
    "......oooo......",
    ".....oDMMLo.....",
    ".....oMLSMo.....",
    "......oMMo......",
    ".....ollllo.....",
    "...oolmmmmloo...",
    "..olmmoooommlo..",
    ".olmo......omdo.",
    ".olmo......omdo.",
    ".olmo......odmo.",
    ".odlmo....omddo.",
    "..odlmmmmmmddo..",
    "...oodddddddo...",
    ".....oooooooo...",
]

AMULET = [
    ".o............o.",
    ".od..........do.",
    "..od........do..",
    "..om........mo..",
    "...od......do...",
    "...om......mo...",
    "....od....do....",
    ".....oooooo.....",
    "....olmmmmdo....",
    "...olmDMMLmdo...",
    "...omDMLSMLdo...",
    "...omMLSLMMdo...",
    "...odMMLMMDdo...",
    "....odDMMDdo....",
    ".....oddddo.....",
    "......oooo......",
]


def paint(rows, metal_layer):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if metal_layer and c in GOLD:
                img.putpixel((x, y), GOLD[c] + (255,))
            if not metal_layer and c in GEM:
                img.putpixel((x, y), GEM[c] + (255,))
    return img


def main():
    for name, rows in (('ring', RING), ('amulet', AMULET)):
        assert all(len(r) == 16 for r in rows) and len(rows) == 16, name
        paint(rows, True).save(os.path.join(OUT, name + '.png'))
        paint(rows, False).save(os.path.join(OUT, name + '_gem.png'))
    print('jewellery textures written')


if __name__ == '__main__':
    main()
