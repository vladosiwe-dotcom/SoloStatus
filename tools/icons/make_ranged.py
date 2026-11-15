"""Textures, models, tags, recipes and Epic Fight capabilities of the Archer's bows and crossbows (SystemRanged.java).

usage: python3 tools/icons/make_ranged.py   (from the repository root)

Drawn as vector strokes at 128x128 and scaled to 32x32, laid out like vanilla's (grip low on the diagonal, the arc
towards the top left) so the vanilla bow/crossbow hand poses fit: a standby state, three drawing states, and for
crossbows a loaded one.
"""
import json
import math
import os

from PIL import Image, ImageDraw, ImageFilter

S, OUT = 128, 32
RES = os.path.join('src', 'main', 'resources')
TEX = os.path.join(RES, 'assets', 'solostatus', 'textures', 'item', 'ranged')
MODELS = os.path.join(RES, 'assets', 'solostatus', 'models', 'item')
DATA = os.path.join(RES, 'data', 'solostatus')

PAL = {
    'hunter': {'wood': (156, 108, 60), 'dark': (96, 64, 36), 'steel': (196, 208, 220), 'string': (236, 236, 236), 'gem': (90, 224, 255), 'arrow': (200, 180, 150)},
    'shadow': {'wood': (78, 58, 104), 'dark': (42, 32, 60), 'steel': (150, 110, 200), 'string': (224, 196, 255), 'gem': (214, 100, 255), 'arrow': (170, 140, 210)},
}


def bezier(a, c, b, n=24):
    return [((1 - t) ** 2 * a[0] + 2 * (1 - t) * t * c[0] + t * t * b[0], (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * c[1] + t * t * b[1])
            for t in (i / n for i in range(n + 1))]


class Canvas:
    def __init__(self):
        self.img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def line(self, pts, colour, width):
        self.d.line(pts, fill=colour + (255,), width=width, joint='curve')
        r = width / 2
        for x, y in (pts[0], pts[-1]):
            self.d.ellipse((x - r, y - r, x + r, y + r), fill=colour + (255,))

    def poly(self, pts, colour):
        self.d.polygon(pts, fill=colour + (255,))

    def dot(self, x, y, r, colour):
        self.d.ellipse((x - r, y - r, x + r, y + r), fill=colour + (255,))


def arrow(cv, tail, length, pal):
    tx, ty = tail
    hx, hy = tx - length, ty - length
    cv.line([(tx, ty), (hx + 6, hy + 6)], pal['arrow'], 5)
    cv.poly([(hx, hy), (hx + 16, hy + 4), (hx + 4, hy + 16)], pal['steel'])
    cv.line([(tx, ty), (tx + 8, ty - 2)], pal['string'], 4)
    cv.line([(tx, ty), (tx - 2, ty + 8)], pal['string'], 4)


def bow(kind, grade, pull):
    pal = PAL[grade]
    cv = Canvas()
    if kind == 'longbow':
        a, b, ctrl, width = (8, 120), (120, 8), (6, 6), 9
    else:  # recurve: shorter, with tips curling forward
        a, b, ctrl, width = (16, 112), (112, 16), (14, 14), 11
    limbs = bezier(a, ctrl, b)
    cv.line(limbs, pal['wood'], width)
    cv.line(limbs[10:15], pal['dark'], width + 2)  # the grip
    cv.dot(*limbs[12], 4, pal['gem'])
    if kind == 'recurve_bow':
        cv.line([a, (a[0] + 12, a[1] + 2)], pal['steel'], 6)
        cv.line([b, (b[0] + 2, b[1] + 12)], pal['steel'], 6)
    else:
        cv.dot(*a, 5, pal['steel'])
        cv.dot(*b, 5, pal['steel'])
    # the string: from tip to tip, drawn back towards the bottom right
    mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
    nock = (mid[0] + 26 * pull, mid[1] + 26 * pull)
    cv.line([a, nock, b], pal['string'], 3)
    if pull > 0:
        arrow(cv, nock, 70 + 10 * pull, pal)
    return cv.img


def crossbow(kind, grade, state):
    """state: 0 standby, 1..3 drawing, 4 loaded."""
    pal = PAL[grade]
    cv = Canvas()
    heavy = kind == 'heavy_crossbow'
    # stock along the diagonal, grip at the bottom right
    cv.line([(44, 44), (116, 116)], pal['wood'], 18 if heavy else 14)
    cv.line([(98, 98), (120, 120)], pal['dark'], 20 if heavy else 16)
    if heavy:
        for t in (60, 80):
            cv.line([(t - 8, t + 8), (t + 8, t - 8)], pal['steel'], 6)
    else:  # the repeater's bolt box on top of the stock
        cv.poly([(60, 40), (84, 64), (72, 76), (48, 52)], pal['dark'])
        cv.poly([(62, 44), (80, 62), (74, 68), (56, 50)], pal['steel'])
    cv.dot(88, 88, 5, pal['gem'])
    # the prod across the front, bulging to the top left
    p1, p2 = ((6, 66), (66, 6)) if heavy else ((14, 62), (62, 14))
    cv.line(bezier(p1, (12, 12), p2), pal['steel'] if heavy else pal['wood'], 11 if heavy else 9)
    draw = {0: 0.0, 1: 0.35, 2: 0.7, 3: 1.0, 4: 1.0}[state]
    nock = (40 + 34 * draw, 40 + 34 * draw)
    cv.line([p1, nock, p2], pal['string'], 3)
    if state == 4:
        arrow(cv, nock, 66, pal)
    return cv.img


def finish(img, grade):
    if grade == 'shadow':  # a faint violet glow around the weapon
        glow = img.getchannel('A').filter(ImageFilter.GaussianBlur(4)).point(lambda v: int(v * 0.35))
        g = Image.new('RGBA', (S, S), PAL['shadow']['gem'] + (0,))
        g.putalpha(glow)
        img = Image.alpha_composite(g, img)
    small = img.resize((OUT, OUT), Image.LANCZOS)
    alpha = small.getchannel('A').point(lambda v: 255 if v > 100 else 0)
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


RECIPES = {
    'longbow': (['  T', ' IS', 'T S'], {'T': 'minecraft:stick', 'I': 'minecraft:iron_ingot', 'S': 'minecraft:string'}),
    'recurve_bow': ([' IS', 'A S', ' IS'], {'I': 'minecraft:iron_ingot', 'S': 'minecraft:string', 'A': 'minecraft:amethyst_shard'}),
    'heavy_crossbow': (['IAI', 'SCS', ' I '], {'I': 'minecraft:iron_ingot', 'A': 'minecraft:amethyst_shard', 'S': 'minecraft:string', 'C': 'minecraft:crossbow'}),
    'repeating_crossbow': (['ACA', ' I ', ' S '], {'I': 'minecraft:iron_ingot', 'A': 'minecraft:amethyst_shard', 'S': 'minecraft:stick', 'C': 'minecraft:crossbow'}),
}


def main():
    os.makedirs(TEX, exist_ok=True)
    tags = {'bow': [], 'crossbow': []}
    for grade in PAL:
        for kind in ('longbow', 'recurve_bow'):
            item = f'{grade}_{kind}'
            for i, pull in enumerate((0, 0.4, 0.75, 1.0)):
                name = item if i == 0 else f'{item}_pulling_{i - 1}'
                finish(bow(kind, grade, pull), grade).save(os.path.join(TEX, name + '.png'))
                model = {'parent': 'minecraft:item/bow', 'textures': {'layer0': f'solostatus:item/ranged/{name}'}}
                if i == 0:
                    model['overrides'] = [
                        {'predicate': {'pulling': 1}, 'model': f'solostatus:item/{item}_pulling_0'},
                        {'predicate': {'pulling': 1, 'pull': 0.65}, 'model': f'solostatus:item/{item}_pulling_1'},
                        {'predicate': {'pulling': 1, 'pull': 0.9}, 'model': f'solostatus:item/{item}_pulling_2'}]
                write_json(os.path.join(MODELS, name + '.json'), model)
            tags['bow'].append(f'solostatus:{item}')
            write_json(os.path.join(DATA, 'capabilities', 'weapons', item + '.json'), {'type': 'epicfight:bow'})
        for kind in ('heavy_crossbow', 'repeating_crossbow'):
            item = f'{grade}_{kind}'
            names = [item, f'{item}_pulling_0', f'{item}_pulling_1', f'{item}_pulling_2', f'{item}_arrow']
            for state, name in enumerate(names):
                finish(crossbow(kind, grade, state), grade).save(os.path.join(TEX, name + '.png'))
                model = {'parent': 'minecraft:item/crossbow', 'textures': {'layer0': f'solostatus:item/ranged/{name}'}}
                if state == 0:
                    model['overrides'] = [
                        {'predicate': {'pulling': 1}, 'model': f'solostatus:item/{item}_pulling_0'},
                        {'predicate': {'pulling': 1, 'pull': 0.58}, 'model': f'solostatus:item/{item}_pulling_1'},
                        {'predicate': {'pulling': 1, 'pull': 1.0}, 'model': f'solostatus:item/{item}_pulling_2'},
                        {'predicate': {'charged': 1}, 'model': f'solostatus:item/{item}_arrow'}]
                write_json(os.path.join(MODELS, name + '.json'), model)
            tags['crossbow'].append(f'solostatus:{item}')
            write_json(os.path.join(DATA, 'capabilities', 'weapons', item + '.json'), {'type': 'epicfight:crossbow'})
    for kind, (pattern, keys) in RECIPES.items():
        used = {c for row in pattern for c in row if c != ' '}
        write_json(os.path.join(DATA, 'recipe', f'hunter_{kind}.json'), {
            'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': pattern,
            'key': {c: {'item': keys[c]} for c in sorted(used)}, 'result': {'id': f'solostatus:hunter_{kind}', 'count': 1}})
        write_json(os.path.join(DATA, 'recipe', f'shadow_{kind}_smithing.json'), {
            'type': 'minecraft:smithing_transform', 'template': {'item': 'minecraft:netherite_upgrade_smithing_template'},
            'base': {'item': f'solostatus:hunter_{kind}'}, 'addition': {'item': 'minecraft:netherite_ingot'},
            'result': {'id': f'solostatus:shadow_{kind}', 'count': 1}})
    for tag, items in tags.items():  # merged into the existing weapon type tags
        path = os.path.join(DATA, 'tags', 'item', 'weapons', tag + '.json')
        values = json.load(open(path))['values'] if os.path.exists(path) else []
        values += [i for i in items if i not in values]
        write_json(path, {'replace': False, 'values': values})
    print('ranged weapons:', sum(len(v) for v in tags.values()))


if __name__ == '__main__':
    main()
