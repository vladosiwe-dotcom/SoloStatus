"""Stat builds compared by what they give, from the formulas in StatFormulas (default config).

usage: python3 tools/balance/builds.py

For every level stage it spends 3 points per level on six builds and prints a melee offence index
(damage x attack speed x average crit), a magic index and an effective-health index (health / (1 - reduction) /
(1 - dodge)), each relative to the balanced build. The game test SoloGameTests.statBuildsBalance checks the same
corridors in the game itself.
"""
BASE, SOFT, MAX = 10, 40, 150


def eff(value):
    inv = max(0, value - BASE)
    return inv if inv <= SOFT else SOFT + (inv - SOFT) * 0.5


def numbers(st):
    e = {k: eff(v) for k, v in st.items()}
    phys = min(0.60, e['STR'] * 0.006)
    aspd = min(0.30, e['AGI'] * 0.004)
    dodge = min(0.15, e['AGI'] * 0.0015)
    crit = min(0.20, e['PER'] * 0.002)
    critd = 0.5 + min(0.3, e['PER'] * 0.003)
    hp = min(30, e['VIT'] * 0.3)
    red = min(0.15, e['VIT'] * 0.0015)
    magic = min(0.50, e['INT'] * 0.005)
    offence = (1 + phys) * (1 + aspd) * (1 + crit * critd)
    ehp = (20 + hp) / (1 - red) / (1 - dodge)
    return offence, 1 + magic, ehp


def build(points, order):
    """Spends points on the stats in order, each up to the maximum value, the rest evenly."""
    st = {k: BASE for k in ('STR', 'AGI', 'PER', 'VIT', 'INT', 'LUCK')}
    if order == 'balanced':
        i = 0
        keys = list(st)
        while points > 0:
            if st[keys[i % 6]] < MAX:
                st[keys[i % 6]] += 1
                points -= 1
            i += 1
        return st
    for k in order:
        add = min(points, MAX - st[k])
        st[k] += add
        points -= add
    return st


BUILDS = {'balanced': 'balanced', 'physical': ['STR', 'VIT'], 'defence': ['VIT', 'STR'], 'mobility': ['AGI', 'STR'],
          'crits': ['PER', 'STR'], 'magic': ['INT', 'VIT']}


def main():
    for level in (10, 40, 70, 100):
        pts = 3 * (level - 1)
        base = numbers(build(pts, 'balanced'))
        print(f'level {level} ({pts} points)')
        for name, order in BUILDS.items():
            o, m, h = numbers(build(pts, order))
            print(f'  {name:9} offence {o / base[0]:5.2f}  magic {m / base[1]:5.2f}  health {h / base[2]:5.2f}')


if __name__ == '__main__':
    main()
