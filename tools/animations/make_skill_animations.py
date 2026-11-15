"""Player animations of the System's active skills, written for this mod (assets/solostatus/player_animations).

usage: python3 tools/animations/make_skill_animations.py   (from the repository root)

The file is in the Blockbench / Bedrock animation format that playerAnimator reads (the library Iron's Spells
brings and animates its own casts with), so every animation can also be opened and polished in Blockbench.
Bones: head, torso, body (the whole player), right_arm, left_arm, right_leg, left_leg. Rotations in degrees: an arm
at x = -90 points forward, near -180 it is raised overhead; for the left arm +y turns it towards the body's middle
(the right arm mirrors it); +z of the right arm lifts it out to the side. Positions in pixels.

Each skill gets its own animation: a motion (swing, overhead blow, spin, thrust, shot, throw, cry, guard, cast,
leap, ground blow...) tuned by the skill (speed, weight, which arm, how many strikes). Every animation starts and ends
at the rest pose, so it blends into whatever the player does next.
"""
import json
import os

OUT = os.path.join('src', 'main', 'resources', 'assets', 'solostatus', 'player_animations', 'skills.json')
BONES = ['head', 'torso', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg']


class Anim:
    def __init__(self, length):
        self.length = length
        self.keys = {}  # bone -> kind -> time -> (vector, easing)

    def key(self, t, ease='easeInOutSine', **bones):
        """bones: name=(rx, ry, rz) or name=((rx, ry, rz), (px, py, pz))."""
        for bone, v in bones.items():
            rot, pos = (v if isinstance(v[0], (tuple, list)) else (v, None))
            self.keys.setdefault(bone, {}).setdefault('rotation', {})[t] = (rot, ease)
            if pos is not None:
                self.keys[bone].setdefault('position', {})[t] = (pos, ease)
        return self

    def json(self):
        bones = {}
        for bone, kinds in self.keys.items():
            out = {}
            for kind, frames in kinds.items():
                frames = dict(frames)
                frames.setdefault(0.0, ((0, 0, 0), 'linear'))
                frames.setdefault(self.length, ((0, 0, 0), 'easeInOutSine'))  # back to rest
                out[kind] = {f'{t:.4f}'.rstrip('0').rstrip('.') if t else '0.0':
                             {'vector': [round(c, 2) for c in vec], 'easing': ease} for t, (vec, ease) in sorted(frames.items())}
            bones[bone] = out
        return {'animation_length': self.length, 'bones': bones}


Z = (0, 0, 0)


def swing(speed=1.0, heavy=False, strikes=1, low=False):
    """A sword slash across from the right to the left, one or several."""
    step = 0.32 / speed
    a = Anim(round(0.25 + step * strikes + 0.3, 3))
    t = 0.0
    lift = -110 if not low else -60
    for i in range(strikes):
        side = 1 if i % 2 == 0 else -1
        a.key(t + step * 0.45, right_arm=(lift, 55 * side, 35 * side), torso=(0, -28 * side, 0), left_arm=(-20, 0, -10))
        a.key(t + step, 'easeInOutSine', right_arm=((-75, -65 * side, -10 * side), (0, 0, -2)), torso=(10 if heavy else 4, 32 * side, 0),
              right_leg=((-12, 0, 0), (0, 0, -2)), left_leg=(10, 0, 0))
        t += step
    return a


def overhead(speed=1.0, both=True, kneel=False):
    """Raised high with both hands and brought down hard."""
    a = Anim(round(1.0 / speed, 3))
    up = 0.35 / speed
    hit = 0.5 / speed
    arms = dict(right_arm=(-195, -12, 8))
    if both:
        arms['left_arm'] = (-195, 12, -8)
    a.key(up, torso=(-12, 0, 0), head=(-15, 0, 0), **arms)
    down = dict(right_arm=((-35, -15, 0), (0, -1, -2)), torso=(30 if kneel else 22, 0, 0), head=(10, 0, 0),
                right_leg=((-25, 0, 0), (0, 0, -3)), left_leg=((20, 0, 0), (0, 0, 2)))
    if both:
        down['left_arm'] = ((-40, 15, 0), (0, -1, -2))
    if kneel:
        down['body'] = (Z, (0, -3, 0))
    a.key(hit, 'easeInOutSine', **down)
    a.key(hit + 0.25 / speed, **down)
    return a


def spin(turns=1, speed=1.0):
    """Arms out, turning on the spot (Whirlwind, Blade Storm)."""
    length = round(0.5 * turns / speed + 0.3, 3)
    a = Anim(length)
    a.key(0.15, right_arm=(-90, 0, 70), left_arm=(-90, 0, -70), torso=(8, 0, 0))
    for i in range(1, turns * 4 + 1):
        a.key(0.15 + i * (length - 0.3) / (turns * 4), 'linear', body=(0, -90 * i, 0))
    a.key(length - 0.15, right_arm=(-90, 0, 70), left_arm=(-90, 0, -70))
    return a


def thrust(speed=1.0, crouch=False, both=False):
    """Pulled back, then a lunge with the point forward (dagger, spear, rapier)."""
    a = Anim(round(0.75 / speed, 3))
    a.key(0.22 / speed, right_arm=((-70, 10, 10), (0, 0, 3)), torso=(0, -20, 0), right_leg=(5, 0, 0),
          **({'left_arm': ((-70, -10, -10), (0, 0, 3))} if both else {}))
    hit = dict(right_arm=((-95, -8, 0), (0, 0, -5)), torso=(15 if crouch else 8, 18, 0), right_leg=((-30, 0, 0), (0, 0, -4)),
               left_leg=((15, 0, 0), (0, 0, 2)))
    if both:
        hit['left_arm'] = ((-95, 8, 0), (0, 0, -5))
    if crouch:
        hit['body'] = (Z, (0, -2, 0))
    a.key(0.38 / speed, **hit)
    a.key(0.52 / speed, **hit)
    return a


def dash(lead_left=False, length=0.6):
    """Leaning into a run, the arms back (or the shield arm leading)."""
    a = Anim(length)
    pose = dict(torso=(28, 0, 0), head=(-20, 0, 0), right_leg=((-35, 0, 0), (0, 0, -3)), left_leg=((30, 0, 0), (0, 0, 2)))
    if lead_left:
        pose.update(left_arm=((-80, 30, 0), (0, 0, -3)), right_arm=(25, 0, 15))
    else:
        pose.update(right_arm=(40, 0, 12), left_arm=(40, 0, -12))
    a.key(0.12, **pose)
    a.key(length - 0.15, **pose)
    return a


def cry(shake=True, length=1.0):
    """Both arms up and out, head thrown back (War Cry, Berserk, Overwhelming Presence)."""
    a = Anim(length)
    up = dict(right_arm=(-160, 0, 30), left_arm=(-160, 0, -30), head=(-30, 0, 0), torso=(-12, 0, 0))
    a.key(0.25, **up)
    if shake:
        a.key(0.4, right_arm=(-150, 0, 38), left_arm=(-150, 0, -38), head=(-26, 0, 0), torso=(-10, 0, 0))
        a.key(0.55, **up)
    a.key(length - 0.2, **up)
    return a


def power_up(length=0.9):
    """Arms down and out, fists clenched, a shudder of power (Bloodlust, Body Reinforcement)."""
    a = Anim(length)
    tense = dict(right_arm=(15, 0, 28), left_arm=(15, 0, -28), torso=(15, 0, 0), head=(12, 0, 0), body=(Z, (0, -1, 0)))
    a.key(0.25, **tense)
    a.key(0.5, right_arm=(10, 0, 34), left_arm=(10, 0, -34), torso=(-10, 0, 0), head=(-15, 0, 0))
    a.key(length - 0.2, right_arm=(10, 0, 34), left_arm=(10, 0, -34), torso=(-10, 0, 0))
    return a


def guard(length=0.8, counter=False):
    """Weapon and arm crossed in front; with counter, a quick cut out of the guard."""
    a = Anim(length)
    g = dict(right_arm=(-80, -45, 0), left_arm=(-85, 45, 0), torso=(10, 0, 0), right_leg=(-8, 0, 0), left_leg=(8, 0, 0))
    a.key(0.12, **g)
    if counter:
        a.key(length * 0.45, **g)
        a.key(length * 0.65, right_arm=((-80, 60, 30), (0, 0, -2)), torso=(5, -30, 0), left_arm=(-30, 0, -10))
    else:
        a.key(length - 0.15, **g)
    return a


def bow_shot(up=False, shots=1, length=None):
    """The bow arm forward, the string drawn to the cheek, released (several times for volleys)."""
    length = length or round(0.4 + 0.35 * shots, 3)
    a = Anim(length)
    pitch = -140 if up else -90
    for i in range(shots):
        t0 = 0.15 + i * 0.35
        a.key(t0, left_arm=(pitch, 10, 0), right_arm=((pitch, -40, 0), (0, 0, 3)), head=(-30 if up else 0, 0, 0), torso=(0, 10, 0))
        a.key(t0 + 0.2, left_arm=(pitch, 10, 0), right_arm=((pitch + 10, -30, 20), (0, 0, 5)), head=(-30 if up else 0, 0, 0))
    return a


def crossbow_burst(shots=4):
    a = Anim(round(0.3 + shots * 0.15 + 0.2, 3))
    for i in range(shots):
        t = 0.2 + i * 0.15
        a.key(t, right_arm=(-90, -12, 0), left_arm=(-90, 18, 0), torso=(0, 8, 0))
        a.key(t + 0.07, right_arm=((-98, -12, 0), (0, 0, 2)), left_arm=((-98, 18, 0), (0, 0, 2)), torso=(-4, 8, 0))
    return a


def throw(speed=1.0):
    """Wound back over the shoulder and flung forward (Arcane Blast, Hex)."""
    a = Anim(round(0.7 / speed, 3))
    a.key(0.25 / speed, right_arm=(-165, 10, 25), torso=(-8, -30, 0), left_arm=(-60, 20, 0))
    a.key(0.42 / speed, right_arm=((-60, -20, 0), (0, 0, -3)), torso=(14, 25, 0), right_leg=(-15, 0, 0), left_leg=(12, 0, 0))
    return a


def cast(both=True, length=0.8, high=False):
    """Hands pushed out (heal, bless, magic), with a light tremble while it works."""
    a = Anim(length)
    pitch = -130 if high else -80
    pose = dict(left_arm=(pitch, 20, 0), torso=(-5, 0, 0), head=(-15 if high else 5, 0, 0))
    if both:
        pose['right_arm'] = (pitch, -20, 0)
    a.key(0.2, **pose)
    trem = {k: (v[0] - 4, v[1], v[2]) if k.endswith('arm') else v for k, v in pose.items()}
    a.key(0.4, **trem)
    a.key(length - 0.2, **pose)
    return a


def summon(length=1.1):
    """Arms rising from low and wide to high above (Sanctuary, Meteor, Holy Light, Shadow Legion)."""
    a = Anim(length)
    a.key(0.25, right_arm=(-30, 0, 45), left_arm=(-30, 0, -45), torso=(10, 0, 0), head=(10, 0, 0))
    a.key(0.65, right_arm=(-175, 0, 20), left_arm=(-175, 0, -20), torso=(-15, 0, 0), head=(-35, 0, 0))
    a.key(length - 0.2, right_arm=(-175, 0, 20), left_arm=(-175, 0, -20), torso=(-15, 0, 0), head=(-35, 0, 0))
    return a


def ground_blow(length=0.9):
    """Down on one knee, a fist (or weapon) driven into the ground (Earthbreaker, Retribution, Seismic Charge)."""
    a = Anim(length)
    a.key(0.3, right_arm=(-170, 0, 15), torso=(-10, 0, 0), body=(Z, (0, 1, 0)))
    hit = dict(right_arm=((-20, 0, 0), (0, -2, -2)), torso=(40, 0, 0), head=(20, 0, 0), body=(Z, (0, -4, 0)),
               right_leg=((-60, 0, 0), (0, 0, -3)), left_leg=((60, 0, 0), (0, 2, 2)), left_arm=(-20, 0, -25))
    a.key(0.45, 'easeInOutSine', **hit)
    a.key(length - 0.2, **hit)
    return a


def leap(length=0.7):
    """Knees tucked, arms flung up — a jump off the air (Air Step)."""
    a = Anim(length)
    a.key(0.15, right_leg=(-70, 0, 0), left_leg=(-50, 0, 0), right_arm=(-150, 0, 40), left_arm=(-150, 0, -40), torso=(-10, 0, 0))
    a.key(length - 0.2, right_leg=(-30, 0, 0), left_leg=(-20, 0, 0), right_arm=(-60, 0, 30), left_arm=(-60, 0, -30))
    return a


def dive_strike(length=0.9):
    """From the air, the weapon held high, then crashing down (Sky Strike)."""
    a = Anim(length)
    a.key(0.2, right_arm=(-200, -10, 10), left_arm=(-190, 10, -10), right_leg=(-40, 0, 0), left_leg=(-30, 0, 0), torso=(-15, 0, 0))
    a.key(0.45, right_arm=((-40, -10, 0), (0, -1, -2)), left_arm=(-45, 10, 0), torso=(35, 0, 0), body=(Z, (0, -3, 0)),
          right_leg=(-50, 0, 0), left_leg=(45, 0, 0))
    return a


def step_back(length=0.6, cover=False):
    """A quick step back, the body pulled low (Vanish, Evasive Roll); with cover, the arms over the face."""
    a = Anim(length)
    pose = dict(torso=(-18, 0, 0), body=(Z, (0, -1, 3)), right_leg=(20, 0, 0), left_leg=(-25, 0, 0))
    pose.update(right_arm=(-130, -50, 0), left_arm=(-130, 50, 0)) if cover else pose.update(right_arm=(30, 0, 25), left_arm=(30, 0, -25))
    a.key(0.15, **pose)
    a.key(length - 0.15, **pose)
    return a


def conceal(length=1.0):
    """Crouched, the cloak drawn over the face, sinking into shadow (Concealment)."""
    a = Anim(length)
    pose = dict(right_arm=(-140, -55, 0), left_arm=(-120, 50, 0), head=(25, 0, 0), torso=(25, 0, 0), body=(Z, (0, -3, 0)),
                right_leg=(-30, 0, 0), left_leg=(25, 0, 0))
    a.key(0.25, **pose)
    a.key(length - 0.15, **pose)
    return a


def point(length=0.9, beckon=False, salute=False):
    """An arm straight out at the target (Hunter's Mark, Duel Challenge); beckoning (Taunt); a salute (Focus)."""
    a = Anim(length)
    if salute:
        pose = dict(right_arm=(-150, -60, 0), head=(-5, 0, 0), torso=(-5, 0, 0))
        a.key(0.25, **pose)
        a.key(length - 0.2, **pose)
        return a
    pose = dict(right_arm=(-100, 0, 0), head=(-5, -10, 0), torso=(0, 15, 0))
    a.key(0.2, **pose)
    if beckon:
        a.key(0.4, right_arm=(-100, 0, 0), left_arm=(-80, 20, 0))
        a.key(0.55, left_arm=(-120, 20, 0))
        a.key(0.7, left_arm=(-80, 20, 0))
    a.key(length - 0.15, **pose)
    return a


def hymn(beats=3, length=1.4):
    """Arms open, swaying to the beat, head nodding (Battle Hymn)."""
    a = Anim(length)
    for i in range(beats):
        t = 0.2 + i * (length - 0.4) / beats
        s = 1 if i % 2 == 0 else -1
        a.key(t, right_arm=(-110, 0, 35 + 10 * s), left_arm=(-110, 0, -35 + 10 * s), head=(-15, 12 * s, 0), torso=(-5, 8 * s, 0))
    return a


def poison(length=0.8):
    """The free hand drawn along the blade, coating it (Poison Blade, Toxic Cloud)."""
    a = Anim(length)
    a.key(0.2, right_arm=(-60, -30, 0), left_arm=(-75, 45, 0), head=(20, 0, 0))
    a.key(0.5, right_arm=(-60, -30, 0), left_arm=((-60, 25, 0), (0, 0, -2)), head=(20, 0, 0))
    return a


def flurry(strikes=4, speed=1.4):
    """Alternating quick stabs with both hands (Flurry, Thousand Cuts)."""
    a = Anim(round(0.3 + strikes * 0.18 / speed + 0.25, 3))
    for i in range(strikes):
        t = 0.2 + i * 0.18 / speed
        r = i % 2 == 0
        a.key(t, right_arm=((-95, -5, 0), (0, 0, -4)) if r else (-60, 10, 10), left_arm=(-60, -10, -10) if r else ((-95, 5, 0), (0, 0, -4)),
              torso=(10, 15 if r else -15, 0))
    return a


SKILLS = {
    # Warrior
    'power_strike': swing(0.9, heavy=True), 'whirlwind': spin(1), 'charge': dash(lead_left=True, length=0.7),
    'war_cry': cry(), 'devastating_blow': overhead(0.8, kneel=True), 'titan_slash': overhead(0.6),
    'berserk': power_up(1.0), 'earthbreaker': ground_blow(1.0), 'duel_challenge': point(0.9),
    # Thief
    'quick_dash': dash(length=0.5), 'flurry': flurry(4), 'vanish': step_back(0.7, cover=True), 'poison_blade': poison(),
    'assassinate': thrust(0.9, crouch=True), 'thousand_cuts': flurry(8, 1.8), 'shadow_step': thrust(1.2),
    'blade_storm': spin(2, 1.2), 'toxic_cloud': poison(0.9), 'concealment': conceal(),
    # Defender
    'shield_bash': dash(lead_left=True, length=0.5), 'taunt': point(1.0, beckon=True), 'defensive_stance': guard(0.9),
    'counter_attack': guard(0.8, counter=True), 'fortress': guard(1.1), 'aegis': cast(both=True, length=1.0, high=True),
    'seismic_charge': ground_blow(0.9), 'unbreakable': power_up(1.1), 'retribution': ground_blow(1.0),
    # Archer
    'power_shot': bow_shot(), 'arrow_volley': bow_shot(shots=3), 'knockback_shot': bow_shot(),
    'rain_of_arrows': bow_shot(up=True, shots=2), 'hunters_mark': point(0.9), 'sniper_shot': bow_shot(length=1.0),
    'evasive_roll': step_back(0.6), 'storm_of_arrows': bow_shot(up=True, shots=4), 'bolt_barrage': crossbow_burst(5),
    # Support
    'mend': cast(both=False), 'blessing': cast(both=True, high=True), 'barrier': guard(0.9), 'purify': cast(both=True),
    'sanctuary': summon(), 'holy_light': summon(1.0), 'hex': throw(1.0), 'battle_hymn': hymn(),
    'divine_intervention': summon(1.2),
    # Mage (System skills; the school spells are animated by Iron's Spells)
    'meteor': summon(1.0), 'gravity_well': cast(both=True, high=True), 'arcane_cataclysm': summon(1.3), 'arcane_strike': swing(1.2),
    # General and awakened
    'parry': guard(0.5), 'riposte': thrust(1.4), 'sky_strike': dive_strike(), 'air_step': leap(), 'sprint': dash(length=0.6),
    'bloodlust': power_up(), 'body_reinforcement': power_up(0.9), 'focus': point(0.8, salute=True),
    'thought_acceleration': point(0.7, salute=True), 'overwhelming_presence': cry(shake=False, length=1.1),
    'shadow_legion': summon(1.2), 'ruler_grip': cast(both=False, length=0.9), 'arcane_blast': throw(1.1), 'mana_blade': swing(1.1),
    'cross_slash': swing(1.3, strikes=2), 'blade_flash': thrust(1.6), 'sunder': overhead(1.1, both=False),
    'piercing_thrust': thrust(1.1), 'ground_pound': ground_blow(0.9), 'frost_arrow': bow_shot(), 'explosive_bolt': crossbow_burst(1),
    'vital_strike': thrust(1.3, crouch=True),
}


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    data = {'format_version': '1.8.0', 'animations': {f'skill_{k}': v.json() for k, v in SKILLS.items()}}
    with open(OUT, 'w') as f:
        json.dump(data, f, indent=1)
        f.write('\n')
    print('animations:', len(SKILLS))


if __name__ == '__main__':
    main()
