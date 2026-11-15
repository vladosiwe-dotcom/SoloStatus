"""Levelling pace of the System: hours of fighting per stage, from the formulas in the code.

usage: python3 tools/balance/pacing.py

Mirrors LevelSystem.xpToNext and the kill reward in CombatEvents.onKill. Assumptions, written down so they can be
argued with: two fair kills a minute; the mobs a hunter fights grow from overworld ones (threat ≈1.3: zombie,
skeleton) to End and raid ones (≈3.3: enderman, vindicator, piglin brute) by level 100; first-kill, boss and
achievement experience adds about a quarter on top of kills.
"""

KILLS_PER_MIN = 2.0
BONUS_SHARE = 1.25
STAGES = [(1, 20, 'Пробуждение', 4), (21, 40, 'Формирование', 12), (41, 70, 'Мастерство', 35), (71, 100, 'Высшее развитие', 60)]


def xp_to_next(level):  # LevelSystem.xpToNext
    return round(7.5 * level ** 1.7 + 320)


def typical_threat(level):
    return 1.3 + 2.0 * (level / 100)


def kill_xp(threat, level):  # CombatEvents.onKill
    expected = 1 + level * 0.025
    gap = max(0.3, min(1.5, threat / (expected * 0.6)))
    return 10 * threat ** 1.1 * gap


def hours(level):
    per_min = kill_xp(typical_threat(level), level) * KILLS_PER_MIN * BONUS_SHARE
    return xp_to_next(level) / per_min / 60


def main():
    total = 0
    for lo, hi, name, target in STAGES:
        h = sum(hours(level) for level in range(max(1, lo - 1), hi))  # from lo-1 to hi
        xp = sum(xp_to_next(level) for level in range(max(1, lo - 1), hi))
        total += h
        print(f'{lo:3}-{hi:<3} {name:16} {xp:>9} опыта  {h:6.1f} ч  (цель {target} ч)')
    print(f'всего до 100: {total:.0f} ч; зомби на 1/50/100 ур.: '
          + ', '.join(f'{kill_xp(1.47, lv):.0f}' for lv in (1, 50, 100)) + ' опыта')


if __name__ == '__main__':
    main()
