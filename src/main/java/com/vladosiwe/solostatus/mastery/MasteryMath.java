package com.vladosiwe.solostatus.mastery;

/** Кривая мастерства. Максимальный уровень — 50. */
public final class MasteryMath {
    public static final int MAX_LEVEL = 50;
    private static final double[] CUMULATIVE = new double[MAX_LEVEL + 1];

    static {
        double total = 0;
        CUMULATIVE[0] = 0;
        for (int l = 1; l <= MAX_LEVEL; l++) {
            total += need(l - 1);
            CUMULATIVE[l] = total;
        }
    }

    private MasteryMath() {}

    /** Опыт, нужный для перехода с уровня level на level+1. */
    public static double need(int level) {
        // first awakening (10) ≈ 165 ordinary kills, 25 ≈ 1 650, 50 ≈ 9 800 (strong enemies count more)
        return 20 * Math.pow(level + 1, 1.6);
    }

    public static int level(double xp) {
        for (int l = MAX_LEVEL; l >= 0; l--) if (xp >= CUMULATIVE[l]) return l;
        return 0;
    }

    /** Прогресс внутри текущего уровня (0..1). */
    public static double progress(double xp) {
        int l = level(xp);
        if (l >= MAX_LEVEL) return 1;
        return (xp - CUMULATIVE[l]) / need(l);
    }

    public static double xpForLevel(int level) {
        return CUMULATIVE[Math.max(0, Math.min(MAX_LEVEL, level))];
    }

    /** Бонус урона от мастерства: +0.5% за уровень (до +25%). */
    public static double damageBonus(int level) {
        return level * 0.005;
    }

    /** Бонус скорости атаки ближнего боя: +0.2% за уровень (до +10%). */
    public static double speedBonus(int level) {
        return level * 0.002;
    }

    /** Бонус шанса крита: +3% на 25 уровне и ещё +3% на 40. */
    public static double critBonus(int level) {
        return (level >= 25 ? 0.03 : 0) + (level >= 40 ? 0.03 : 0);
    }
}
