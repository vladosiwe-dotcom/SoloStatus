"""Glyphs of the later specialization steps (textures/gui/skill/<id>.png, StyleSkills): the sign of the skill they
grow from, a little smaller, with a mark of the step in the upper right corner — a rising chevron for the level 30
development of the main skill, a four-pointed star for the level 60 style. White 64x64, tinted when drawn.

usage: python3 tools/icons/make_style_glyphs.py   (from the repository root)
"""
import json
import math
import os

from PIL import Image, ImageDraw

DIR = os.path.join('src', 'main', 'resources', 'assets', 'solostatus', 'textures', 'gui', 'skill')
S = 256

# (main skill, level 30 development, spec passive, level 60 style) — the order of Skills.style()
STYLES = [
    ('berserk', 'bloodbath', 'blood_rage', 'undying_rage'),
    ('earthbreaker', 'aftershock', 'commander', 'thick_of_battle'),
    ('shadow_step', 'shadow_echo', 'shadow_veil', 'night_blade'),
    ('blade_storm', 'storm_edge', 'blade_rhythm', 'endless_dance'),
    ('meteor', 'meteor_shower', 'elemental_attunement', 'elemental_overload'),
    ('gravity_well', 'event_horizon', 'arcane_flow', 'arcane_overflow'),
    ('aegis', 'mirror_aegis', 'bulwark', 'stalwart'),
    ('seismic_charge', 'unstoppable', 'thorns_body', 'colossus'),
    ('sniper_shot', 'killshot', 'deadeye', 'eagle_eye'),
    ('evasive_roll', 'volley_roll', 'fleet_foot', 'wind_runner'),
    ('holy_light', 'radiance', 'divine_grace', 'seraph_ward'),
    ('hex', 'spreading_hex', 'resonance', 'deep_curse'),
    ('duel_challenge', 'decisive_duel', 'fencing', 'en_garde'),
    ('toxic_cloud', 'virulent_cloud', 'venom_mastery', 'toxic_burst'),
    ('arcane_strike', 'runic_edge', 'battle_magic', 'mana_conduit'),
    ('retribution', 'wrath_unleashed', 'vengeance', 'eye_for_eye'),
    ('bolt_barrage', 'explosive_finale', 'auto_reload', 'heavy_bolts'),
    ('battle_hymn', 'encore', 'melody_of_life', 'song_of_valor'),
]


def chevron(d, cx, cy, r):
    for k in (0, 1):
        y = cy + r * 0.55 - k * r * 0.8
        d.line([(cx - r, y + r * 0.5), (cx, y - r * 0.3), (cx + r, y + r * 0.5)], fill=255, width=int(r * 0.42), joint='curve')


def star(d, cx, cy, r):
    pts = []
    for i in range(8):
        rr = r if i % 2 == 0 else r * 0.38
        a = math.radians(-90 + i * 45)
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    d.polygon(pts, fill=255)


def compose(base, mark):
    src = Image.open(os.path.join(DIR, base + '.png')).convert('RGBA').getchannel('A').resize((S, S), Image.LANCZOS)
    mask = Image.new('L', (S, S), 0)
    mask.paste(src.resize((200, 200), Image.LANCZOS), (8, 48))  # the sign, lower left
    d = ImageDraw.Draw(mask)
    cx, cy, r = 204, 52, 42
    d.ellipse((cx - r - 12, cy - r - 12, cx + r + 12, cy + r + 12), fill=0)  # a clear ring around the mark
    (chevron if mark == 'chevron' else star)(d, cx, cy, r)
    img = Image.new('RGBA', (64, 64), (255, 255, 255, 0))
    img.putalpha(mask.resize((64, 64), Image.LANCZOS))
    return img


def main():
    n = 0
    for main_skill, develop, passive, style in STYLES:
        for base, name, mark in ((main_skill, develop, 'chevron'), (passive, style, 'star')):
            compose(base, mark).save(os.path.join(DIR, name + '.png'))
            with open(os.path.join(DIR, name + '.png.mcmeta'), 'w') as f:
                json.dump({'texture': {'blur': True}}, f)
            n += 1
    print('style glyphs:', n)


if __name__ == '__main__':
    main()
