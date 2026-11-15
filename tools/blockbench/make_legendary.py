"""The six legendary class weapons as Blockbench models (LegendaryWeapons.java).

usage: python3 tools/blockbench/make_legendary.py   (from the repository root)

Every weapon is a list of boxes (Blockbench cubes) in the Java item model format, built around its grip at (8, 8, 8)
so one set of hand transforms fits them all. For each weapon this writes:
  - tools/blockbench/<id>.bbmodel: the Blockbench project (open it in Blockbench to edit; export as Java item model
    to src/main/resources/assets/solostatus/models/item/<id>.json and the texture to textures/item/legendary/<id>.png);
  - the game's item model (with extra models for the bow's draw and the shield's block);
  - the texture: a 64x64 sheet of 16 material patches (4x4 UV units each) that the faces map onto;
  - the inventory icon: a 32x32 drawing of the weapon (the 3D model is used in hand, on the ground and in frames);
  - the Epic Fight moveset and the weapon type tags.
Glowing parts (gems, runes) are full-bright through NeoForge's element data.
"""
import base64
import io
import json
import math
import os
import random
import uuid

from PIL import Image, ImageDraw, ImageFilter

RES = os.path.join('src', 'main', 'resources')
MODELS = os.path.join(RES, 'assets', 'solostatus', 'models', 'item')
TEX = os.path.join(RES, 'assets', 'solostatus', 'textures', 'item', 'legendary')
DATA = os.path.join(RES, 'data', 'solostatus')
BB = os.path.join('tools', 'blockbench')

# material -> (patch index, base colour, glows)
MATS = {
    'steel': (0, (196, 206, 222), False), 'steel_dark': (1, (92, 100, 118), False), 'gold': (2, (232, 186, 74), False),
    'gold_dark': (3, (158, 112, 40), False), 'leather': (4, (96, 58, 38), False), 'gem_cyan': (5, (80, 228, 255), True),
    'gem_violet': (6, (190, 96, 255), True), 'gem_gold': (7, (255, 214, 92), True), 'obsidian': (8, (34, 28, 48), False),
    'white': (9, (236, 232, 222), False), 'wood': (10, (122, 84, 52), False), 'string': (11, (238, 238, 232), False),
    'rune': (12, (110, 200, 255), True), 'crimson': (13, (168, 36, 48), False), 'violet_steel': (14, (88, 60, 128), False),
    'silver': (15, (200, 220, 240), False),
}


def patch_texture():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(7)
    for name, (i, c, glow) in MATS.items():
        x0, y0 = (i % 4) * 16, (i // 4) * 16
        for y in range(16):
            for x in range(16):
                k = 1.18 - 0.36 * y / 15  # light at the top of a face, darker below
                if not glow:
                    k += rnd.uniform(-0.05, 0.05)
                if x in (0, 15) or y in (0, 15):
                    k *= 0.72  # a darker rim so the boxes read
                if name in ('steel', 'silver') and 6 <= x <= 7:
                    k *= 1.12  # a sheen line
                px[x0 + x, y0 + y] = tuple(max(0, min(255, int(v * k))) for v in c) + (255,)
    return img


def uv(mat):
    i = MATS[mat][0]
    u, v = (i % 4) * 4, (i // 4) * 4
    return [u + 0.25, v + 0.25, u + 3.75, v + 3.75]


class Model:
    def __init__(self, name):
        self.name = name
        self.boxes = []

    def box(self, label, a, b, mat, rot=None, shift=(0, 0, 0)):
        """a, b: corners relative to the grip; rot: (axis, angle, origin relative to the grip)."""
        f = [round(a[k] + 8 + shift[k], 4) for k in range(3)]
        t = [round(b[k] + 8 + shift[k], 4) for k in range(3)]
        for v in f + t:
            assert -16 <= v <= 32, (self.name, label, v)
        self.boxes.append((label, f, t, mat, rot and (rot[0], rot[1], [round(rot[2][k] + 8 + shift[k], 4) for k in range(3)])))
        return self

    def elements(self):
        out = []
        for label, f, t, mat, rot in self.boxes:
            e = {'name': label, 'from': f, 'to': t, 'faces': {d: {'uv': uv(mat), 'texture': '#0'} for d in
                                                               ('north', 'east', 'south', 'west', 'up', 'down')}}
            if rot:
                e['rotation'] = {'angle': rot[1], 'axis': rot[0], 'origin': rot[2]}
            if MATS[mat][2]:
                e['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
                e['shade'] = False
            out.append(e)
        return out


HAND = {  # the grip is at the model's centre, the weapon points up (+y)
    'thirdperson_righthand': {'rotation': [0, -90, 0], 'translation': [0, 1, 1], 'scale': [0.68, 0.68, 0.68]},
    'thirdperson_lefthand': {'rotation': [0, 90, 0], 'translation': [0, 1, 1], 'scale': [0.68, 0.68, 0.68]},
    'firstperson_righthand': {'rotation': [0, -90, 20], 'translation': [1.13, 3.2, 1.13], 'scale': [0.55, 0.55, 0.55]},
    'firstperson_lefthand': {'rotation': [0, 90, -20], 'translation': [1.13, 3.2, 1.13], 'scale': [0.55, 0.55, 0.55]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.35, 0.35, 0.35]},
    'fixed': {'rotation': [0, 180, 45], 'translation': [0, 0, 0], 'scale': [0.45, 0.45, 0.45]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [0.8, 0.8, 0.8]},
}
SHIELD = {
    'thirdperson_righthand': {'rotation': [0, 90, 0], 'translation': [8, 0, -5], 'scale': [0.7, 0.7, 0.7]},
    'thirdperson_lefthand': {'rotation': [0, 90, 0], 'translation': [8, 0, -5], 'scale': [0.7, 0.7, 0.7]},
    'firstperson_righthand': {'rotation': [0, 180, 5], 'translation': [-6, 2, -8], 'scale': [0.8, 0.8, 0.8]},
    'firstperson_lefthand': {'rotation': [0, 180, 5], 'translation': [6, 2, -8], 'scale': [0.8, 0.8, 0.8]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.35, 0.35, 0.35]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
}
SHIELD_BLOCKING = dict(SHIELD, **{
    'thirdperson_righthand': {'rotation': [45, 135, 0], 'translation': [4, 1, -3], 'scale': [0.7, 0.7, 0.7]},
    'thirdperson_lefthand': {'rotation': [45, 135, 0], 'translation': [4, 1, -3], 'scale': [0.7, 0.7, 0.7]},
    'firstperson_righthand': {'rotation': [0, 180, -5], 'translation': [-10, 0, -6], 'scale': [0.8, 0.8, 0.8]},
    'firstperson_lefthand': {'rotation': [0, 180, -5], 'translation': [10, 0, -6], 'scale': [0.8, 0.8, 0.8]},
})
BOW = dict(HAND, **{
    'thirdperson_righthand': {'rotation': [0, -90, -10], 'translation': [0, 0, 1], 'scale': [0.62, 0.62, 0.62]},
    'thirdperson_lefthand': {'rotation': [0, 90, 10], 'translation': [0, 0, 1], 'scale': [0.62, 0.62, 0.62]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.5, 0.5, 0.5]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.5, 0.5, 0.5]},
})


# ---------------------------------------------------------------- the weapons

def mountain_cleaver():
    m = Model('mountain_cleaver')  # the Warrior's greatsword
    m.box('pommel', (-1.25, -10, -1.25), (1.25, -8, 1.25), 'gold')
    m.box('grip', (-0.75, -8, -0.75), (0.75, 1, 0.75), 'leather')
    m.box('guard', (-5.5, 1, -1.5), (5.5, 3, 1.5), 'gold_dark')
    m.box('guard_left', (-6.5, 0, -1), (-5.5, 4.5, 1), 'gold')
    m.box('guard_right', (5.5, 0, -1), (6.5, 4.5, 1), 'gold')
    m.box('guard_gem', (-1, 1.5, -1.8), (1, 3.5, 1.8), 'gem_cyan')
    m.box('blade', (-3, 3, -0.5), (3, 20.5, 0.5), 'steel_dark')
    m.box('edge_left', (-4, 3, -0.25), (-3, 19.5, 0.25), 'steel')
    m.box('edge_right', (3, 3, -0.25), (4, 19.5, 0.25), 'steel')
    m.box('tip', (-2.6, 18.2, -0.4), (2.6, 23.4, 0.4), 'steel', ('z', 45, (0, 20.8, 0)))
    m.box('rune', (-0.4, 5, -0.65), (0.4, 18, 0.65), 'rune')
    return m


def dusk_fang():
    m = Model('dusk_fang')  # the Thief's dagger
    m.box('pommel', (-0.9, -5, -0.9), (0.9, -3.5, 0.9), 'gem_violet')
    m.box('grip', (-0.6, -3.5, -0.6), (0.6, 2, 0.6), 'obsidian')
    m.box('guard', (-2.5, 2, -1), (2.5, 3, 1), 'violet_steel')
    m.box('fang_left', (-3.4, 1.4, -0.5), (-2.4, 4.4, 0.5), 'silver', ('z', 22.5, (-2.9, 2.4, 0)))
    m.box('fang_right', (2.4, 1.4, -0.5), (3.4, 4.4, 0.5), 'silver', ('z', -22.5, (2.9, 2.4, 0)))
    m.box('blade_low', (-1.5, 3, -0.4), (1.5, 9, 0.4), 'violet_steel')
    m.box('edge_low', (1.5, 3, -0.2), (2, 9, 0.2), 'silver')
    m.box('blade_high', (-1.3, 8.6, -0.35), (1.2, 13, 0.35), 'violet_steel', ('z', 22.5, (0, 9, 0)))
    m.box('tip', (-0.8, 12.6, -0.3), (0.6, 15.4, 0.3), 'silver', ('z', 22.5, (0, 9, 0)))
    m.box('vein', (-0.25, 3.5, -0.5), (0.25, 8.5, 0.5), 'gem_violet')
    return m


def starwell_staff():
    m = Model('starwell_staff')  # the Mage's staff
    m.box('foot', (-0.75, -15, -0.75), (0.75, -14, 0.75), 'gold')
    m.box('shaft', (-0.6, -14, -0.6), (0.6, 14, 0.6), 'obsidian')
    for y in (-6, 2, 10):
        m.box(f'band_{y}', (-0.8, y, -0.8), (0.8, y + 1, 0.8), 'gold')
    m.box('collar', (-1.2, 13, -1.2), (1.2, 15, 1.2), 'gold_dark')
    o = (0, 19.5, 0)
    for label, a, b in (('ring_left', (-4.5, 15.5, -0.5), (-3.5, 23.5, 0.5)), ('ring_right', (3.5, 15.5, -0.5), (4.5, 23.5, 0.5)),
                        ('ring_top', (-3.5, 22.5, -0.5), (3.5, 23.5, 0.5)), ('ring_bottom', (-3.5, 15.5, -0.5), (3.5, 16.5, 0.5))):
        m.box(label, a, b, 'gold', ('z', 45, o))
    m.box('star', (-1.6, 17.9, -1.6), (1.6, 21.1, 1.6), 'gem_cyan', ('y', 45, o))
    m.box('star_core', (-0.8, 18.7, -2), (0.8, 20.3, 2), 'white', ('z', 45, o))
    return m


def last_bastion():
    m = Model('last_bastion')  # the Defender's shield, the front faces -z
    m.box('board', (-6, -9, 0), (6, 11, 1.5), 'steel_dark')
    m.box('rim_left', (-7, -9, -0.5), (-6, 12, 2), 'gold')
    m.box('rim_right', (6, -9, -0.5), (7, 12, 2), 'gold')
    m.box('rim_top', (-7, 11, -0.5), (7, 12, 2), 'gold')
    m.box('point', (-4.24, -13.24, 0.1), (4.24, -4.76, 1.4), 'steel_dark', ('z', 45, (0, -9, 0)))
    m.box('point_rim', (-4.4, -13.4, 0.6), (4.4, -4.6, 1.9), 'gold_dark', ('z', 45, (0, -9, 0)))
    m.box('cross_v', (-1, -7, -0.6), (1, 9, 0), 'white')
    m.box('cross_h', (-4.5, 2, -0.6), (4.5, 4, 0), 'white')
    m.box('emblem', (-1.6, 1.4, -1.2), (1.6, 4.6, -0.4), 'gem_gold')
    m.box('handle', (-1, -1, 1.5), (1, 5, 3), 'leather')
    return m


def skyhunt_bow(state):
    """state 0 standby, 1..3 drawing (the string and an arrow drawn back)."""
    m = Model('skyhunt_bow' + ('' if state == 0 else f'_pulling_{state - 1}'))
    m.box('grip', (-0.8, -2.5, -0.8), (0.8, 2.5, 0.8), 'leather')
    m.box('gem', (-1.2, -0.6, -1.0), (0.2, 0.6, 1.0), 'gem_cyan')
    flex = [0, 0, 0.6, 1.2][state]
    tips = []
    for sign in (1, -1):
        y0 = 2.5 * sign
        # three limb segments bending back (+x) more and more, as a recurve
        segs = [(4, 0), (4, 22.5), (4, 45)]
        x, y = 0.0, y0
        for i, (length, angle) in enumerate(segs):
            ang = angle + flex * 4 * (i + 1) / 3
            ang = min(45, round(ang / 22.5) * 22.5) if i else 0
            a = math.radians(ang)
            # a segment from (x, y) of this length, tilted towards +x (rotation about z: positive turns the top to -x)
            cy = y + sign * length / 2 * math.cos(a)
            cx = x + length / 2 * math.sin(a)
            lo, hi = (cy - length / 2, cy + length / 2)
            m.box(f'limb_{"up" if sign > 0 else "down"}_{i}', (cx - 0.6, lo, -0.6), (cx + 0.6, hi, 0.6), 'gold' if i == 2 else 'wood',
                  ang and ('z', -ang * sign, (cx, cy, 0)))
            x += length * math.sin(a)
            y += sign * length * math.cos(a)
        m.box(f'tip_{"up" if sign > 0 else "down"}', (x - 0.8, y - 0.8, -0.8), (x + 0.8, y + 0.8, 0.8), 'gem_cyan')
        tips.append((x, y))
    (tx, ty), _ = tips
    if state == 0:
        m.box('string', (tx - 0.15, -ty, -0.15), (tx + 0.15, ty, 0.15), 'string')
    else:
        # the string drawn back along 22.5 degree segments (the angles a Java model allows), from the tips to the nock
        pull = 0 if state == 1 else ty * math.tan(math.radians(22.5))
        nx = tx + pull
        for sign in (1, -1):
            dy = ty
            length = math.hypot(pull, dy)
            ang = math.degrees(math.atan2(pull, dy))
            ang = round(ang / 22.5) * 22.5
            cx, cy = (tx + nx) / 2, sign * dy / 2
            m.box(f'string_{"up" if sign > 0 else "down"}', (cx - 0.15, cy - length / 2, -0.15), (cx + 0.15, cy + length / 2, 0.15), 'string',
                  ang and ('z', ang * sign, (cx, cy, 0)))
        m.box('arrow', (nx - 15, -0.3, -0.3), (nx, 0.3, 0.3), 'wood')
        m.box('arrow_head', (nx - 17, -0.7, -0.7), (nx - 15, 0.7, 0.7), 'gem_cyan' if state == 3 else 'silver')
        m.box('fletching', (nx - 2.5, -1, -0.15), (nx, 1, 0.15), 'white')
    return m


def dawn_scepter():
    m = Model('dawn_scepter')  # the Support's sceptre
    m.box('pommel', (-1, -9, -1), (1, -7.5, 1), 'gold')
    m.box('grip', (-0.75, -7.5, -0.75), (0.75, 1, 0.75), 'leather')
    m.box('shaft', (-0.6, 1, -0.6), (0.6, 12, 0.6), 'white')
    m.box('band', (-0.8, 5, -0.8), (0.8, 6, 0.8), 'gold')
    m.box('collar', (-1.3, 12, -1.3), (1.3, 13.5, 1.3), 'gold')
    o = (0, 16, 0)
    m.box('sun', (-1.8, 14.2, -1.8), (1.8, 17.8, 1.8), 'gem_gold', ('y', 45, o))
    m.box('ray_up', (-0.4, 17.8, -0.4), (0.4, 21.5, 0.4), 'gold')
    m.box('ray_left', (-5.5, 15.6, -0.4), (-2, 16.4, 0.4), 'gold')
    m.box('ray_right', (2, 15.6, -0.4), (5.5, 16.4, 0.4), 'gold')
    for ang in (45, -45):
        m.box(f'ray_{ang}', (-0.4, 18, -0.4), (0.4, 21, 0.4), 'gold', ('z', ang, o))
        m.box(f'ray_low_{ang}', (-0.4, 11, -0.4), (0.4, 14, 0.4), 'gold', ('z', ang, o))
    m.box('halo', (-3.2, 15.8, -0.2), (3.2, 16.2, 0.2), 'gem_gold', ('y', 45, o))
    return m


WEAPONS = {
    'mountain_cleaver': ('greatsword', 'epicfight:greatsword', mountain_cleaver),
    'dusk_fang': ('dagger', 'epicfight:dagger', dusk_fang),
    'starwell_staff': ('staff', 'epicfight:spear', starwell_staff),
    'last_bastion': ('shield', 'epicfight:shield', last_bastion),
    'skyhunt_bow': ('bow', 'epicfight:bow', None),
    'dawn_scepter': ('mace', 'epicfight:axe', dawn_scepter),
}


# ---------------------------------------------------------------- inventory icons (2D)

SKILLS = {'mountain_cleaver': 'mountain_fall', 'dusk_fang': 'thousand_shadows', 'starwell_staff': 'star_well',
          'last_bastion': 'unbreakable_line', 'skyhunt_bow': 'heavens_verdict', 'dawn_scepter': 'daybreak'}
GLYPHS = os.path.join(RES, 'assets', 'solostatus', 'textures', 'gui', 'skill')


def glyph(name):
    """The legendary skill's sign (textures/gui/skill/<skill>.png): the weapon's silhouette in white, 64x64."""
    shape = drawing(name)
    a = shape.getchannel('A').resize((56, 56), Image.LANCZOS)
    img = Image.new('RGBA', (64, 64), (255, 255, 255, 0))
    mask = Image.new('L', (64, 64), 0)
    mask.paste(a, (4, 4))
    img.putalpha(mask)
    return img


def icon(name):
    """A 32x32 inventory drawing: the weapon on the diagonal, like vanilla's held items, with an outline."""
    img = drawing(name)
    small = img.resize((32, 32), Image.LANCZOS)
    alpha = small.getchannel('A').point(lambda v: 255 if v > 90 else 0)
    small.putalpha(alpha)
    out = Image.new('RGBA', (32, 32), (14, 10, 22, 0))
    out.putalpha(alpha.filter(ImageFilter.MaxFilter(3)))
    out.alpha_composite(small)
    return out


def drawing(name):
    S = 128
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = {k: v[1] + (255,) for k, v in MATS.items()}

    def seg(a, b, col, w):
        d.line([a, b], fill=col, width=w)
        for x, y in (a, b):
            d.ellipse((x - w / 2, y - w / 2, x + w / 2, y + w / 2), fill=col)

    if name == 'mountain_cleaver':
        d.polygon([(30, 86), (100, 16), (118, 10), (112, 28), (42, 98)], fill=c['steel_dark'])
        d.polygon([(30, 86), (100, 16), (106, 14), (36, 84)], fill=c['steel'])
        seg((44, 80), (96, 28), c['rune'], 4)
        seg((22, 70), (58, 106), c['gold_dark'], 10)
        seg((10, 118), (32, 96), c['leather'], 9)
        d.ellipse((4, 112, 16, 124), fill=c['gold'])
        d.ellipse((34, 82, 46, 94), fill=c['gem_cyan'])
    elif name == 'dusk_fang':
        d.polygon([(50, 76), (82, 40), (100, 22), (118, 12), (108, 32), (92, 56), (60, 86)], fill=c['violet_steel'])
        seg((60, 70), (98, 30), c['gem_violet'], 3)
        seg((40, 64), (74, 98), c['silver'], 8)
        seg((22, 106), (50, 78), c['obsidian'], 10)
        d.ellipse((12, 106, 26, 120), fill=c['gem_violet'])
    elif name == 'starwell_staff':
        seg((14, 114), (82, 46), c['obsidian'], 8)
        for t in (0.35, 0.6, 0.8):
            x, y = 14 + 68 * t, 114 - 68 * t
            seg((x - 3, y - 3), (x + 3, y + 3), c['gold'], 9)
        d.polygon([(96, 6), (122, 32), (96, 58), (70, 32)], outline=c['gold'], width=7)
        d.polygon([(96, 18), (110, 32), (96, 46), (82, 32)], fill=c['gem_cyan'])
        d.ellipse((92, 28, 100, 36), fill=c['white'])
    elif name == 'last_bastion':
        d.polygon([(24, 10), (104, 10), (104, 72), (64, 122), (24, 72)], fill=c['gold'])
        d.polygon([(32, 18), (96, 18), (96, 68), (64, 110), (32, 68)], fill=c['steel_dark'])
        d.rectangle((58, 24, 70, 98), fill=c['white'])
        d.rectangle((38, 46, 90, 58), fill=c['white'])
        d.ellipse((54, 42, 74, 62), fill=c['gem_gold'])
    elif name == 'skyhunt_bow' or name.startswith('skyhunt_bow'):
        d.arc((-40, -40, 112, 112), 290, 160, fill=c['wood'], width=10)
        seg((10, 112), (16, 106), c['gem_cyan'], 12)
        seg((112, 10), (106, 16), c['gem_cyan'], 12)
        seg((12, 110), (110, 12), c['string'], 2)
        seg((68, 66), (76, 58), c['leather'], 12)
    elif name == 'dawn_scepter':
        seg((16, 112), (76, 52), c['white'], 8)
        seg((14, 114), (40, 88), c['leather'], 10)
        seg((72, 56), (80, 48), c['gold'], 14)
        for k in range(8):
            a = math.radians(k * 45 + 22.5)
            seg((94, 34), (94 + 28 * math.cos(a), 34 + 28 * math.sin(a)), c['gold'], 6)
        d.ellipse((80, 20, 108, 48), fill=c['gem_gold'])
        d.ellipse((88, 28, 100, 40), fill=c['white'])
    return img


# ---------------------------------------------------------------- writing

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def bbmodel(model, display, texture_png):
    """A Blockbench project (Java item model format) with the texture embedded."""
    ids = []
    elements = []
    for e in model.elements():
        u = str(uuid.uuid5(uuid.NAMESPACE_URL, model.name + e['name']))
        ids.append(u)
        el = {'name': e['name'], 'box_uv': False, 'rescale': False, 'locked': False, 'render_order': 'default', 'allow_mirror_modeling': True,
              'from': e['from'], 'to': e['to'], 'autouv': 0, 'color': 0, 'origin': [8, 8, 8],
              'faces': {k: {'uv': v['uv'], 'texture': 0} for k, v in e['faces'].items()}, 'type': 'cube', 'uuid': u}
        if 'rotation' in e:
            r = [0, 0, 0]
            r['xyz'.index(e['rotation']['axis'])] = e['rotation']['angle']
            el['rotation'] = r
            el['origin'] = e['rotation']['origin']
        if not e.get('shade', True):
            el['shade'] = False
        elements.append(el)
    buf = io.BytesIO()
    texture_png.save(buf, format='PNG')
    return {
        'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
        'name': model.name, 'parent': '', 'ambientocclusion': True, 'front_gui_light': False,
        'visible_box': [1, 1, 0], 'variable_placeholders': '', 'variable_placeholder_buttons': [],
        'unhandled_root_fields': {},
        'resolution': {'width': 16, 'height': 16},
        'elements': elements, 'outliner': ids,
        'textures': [{'path': '', 'name': model.name + '.png', 'folder': 'item/legendary', 'namespace': 'solostatus', 'id': '0',
                      'width': 64, 'height': 64, 'uv_width': 16, 'uv_height': 16, 'particle': True, 'use_as_default': False,
                      'layers_enabled': False, 'sync_to_project': '', 'render_mode': 'default', 'render_sides': 'auto',
                      'frame_time': 1, 'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False,
                      'visible': True, 'internal': True, 'saved': True, 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, model.name + 'texture')),
                      'relative_path': '', 'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}],
        'display': display,
    }


def game_model(model, display, name, overrides=None):
    tex = f'solostatus:item/legendary/{name}'
    data = {'credit': 'Solo Status, tools/blockbench/make_legendary.py', 'textures': {'0': tex, 'particle': tex},
            'elements': model.elements(), 'display': display, 'gui_light': 'front'}
    if overrides:
        data['overrides'] = overrides
    return data


def separate(name, model_json):
    """The 3D model in hand and on the ground, the drawn icon in the inventory."""
    return {'loader': 'neoforge:separate_transforms', 'gui_light': 'front',
            'base': model_json,
            'perspectives': {'gui': {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'solostatus:item/legendary/{name}_icon'}}}}


def main():
    os.makedirs(TEX, exist_ok=True)
    tex = patch_texture()
    tags = {}
    for name, (wtype, ef, build) in WEAPONS.items():
        tex.save(os.path.join(TEX, name + '.png'))
        icon(name).save(os.path.join(TEX, name + '_icon.png'))
        glyph(name).save(os.path.join(GLYPHS, SKILLS[name] + '.png'))
        with open(os.path.join(GLYPHS, SKILLS[name] + '.png.mcmeta'), 'w') as f:
            json.dump({'texture': {'blur': True}}, f)
        tags.setdefault(wtype, []).append(f'solostatus:{name}')
        if name == 'skyhunt_bow':
            for state in range(4):
                m = skyhunt_bow(state)
                over = None
                if state == 0:
                    over = [{'predicate': {'pulling': 1}, 'model': 'solostatus:item/skyhunt_bow_pulling_0'},
                            {'predicate': {'pulling': 1, 'pull': 0.65}, 'model': 'solostatus:item/skyhunt_bow_pulling_1'},
                            {'predicate': {'pulling': 1, 'pull': 0.9}, 'model': 'solostatus:item/skyhunt_bow_pulling_2'}]
                body = game_model(m, BOW, name)
                full = separate(name, body)
                if over:
                    full['overrides'] = over
                write_json(os.path.join(MODELS, m.name + '.json'), full)
                with open(os.path.join(BB, m.name + '.bbmodel'), 'w') as f:
                    json.dump(bbmodel(m, BOW, tex), f)
        elif name == 'last_bastion':
            m = build()
            full = separate(name, game_model(m, SHIELD, name))
            full['overrides'] = [{'predicate': {'blocking': 1}, 'model': 'solostatus:item/last_bastion_blocking'}]
            write_json(os.path.join(MODELS, name + '.json'), full)
            write_json(os.path.join(MODELS, name + '_blocking.json'), separate(name, game_model(m, SHIELD_BLOCKING, name)))
            with open(os.path.join(BB, name + '.bbmodel'), 'w') as f:
                json.dump(bbmodel(m, SHIELD, tex), f)
        else:
            m = build()
            write_json(os.path.join(MODELS, name + '.json'), separate(name, game_model(m, HAND, name)))
            with open(os.path.join(BB, name + '.bbmodel'), 'w') as f:
                json.dump(bbmodel(m, HAND, tex), f)
        if ef:
            heavy = wtype == 'greatsword'
            write_json(os.path.join(DATA, 'capabilities', 'weapons', name + '.json'), {
                'type': ef, 'attributes': {'common': {'armor_negation': 20.0 if heavy else 12.0, 'impact': 3.5 if heavy else 1.8,
                                                      'max_strikes': 3 if heavy else 1}}} if ef not in ('epicfight:bow', 'epicfight:shield') else {'type': ef})
    for tag, items in tags.items():  # merged into the weapon type tags
        path = os.path.join(DATA, 'tags', 'item', 'weapons', tag + '.json')
        values = json.load(open(path))['values'] if os.path.exists(path) else []
        values += [i for i in items if i not in values]
        write_json(path, {'replace': False, 'values': values})
    print('legendary weapons:', len(WEAPONS))


if __name__ == '__main__':
    main()
