package com.vladosiwe.solostatus.stats;

import com.vladosiwe.solostatus.config.SoloConfig;
import com.vladosiwe.solostatus.data.PlayerData;

/**
 * Формулы производных значений. Используются и сервером (применение), и клиентом (отображение).
 *
 * Принцип мягкого ограничения: каждое очко сверх базовых 10 даёт полный эффект до statSoftCap,
 * затем половину. Каждый эффект дополнительно ограничен жёстким потолком — одна характеристика
 * не может объективно превзойти остальные.
 */
public final class StatFormulas {
    private StatFormulas() {}

    public static final double CAP_PHYS = 0.60, CAP_MOVE = 0.25, CAP_ATK_SPEED = 0.30, CAP_DODGE = 0.15,
            CAP_PROJ = 0.60, CAP_CRIT = 0.20, CAP_HEALTH = 40, CAP_REDUCTION = 0.15, CAP_DEBUFF = 0.25,
            CAP_MAGIC = 0.50, CAP_DROP = 0.20, CAP_LUCK_ATTR = 4.0, CAP_QUALITY = 0.25;

    /** Эффективные очки характеристики с мягким ограничением. */
    public static double eff(PlayerData d, Stat s) {
        return effOf(d.stat(s));
    }

    public static double effOf(int value) {
        int invested = Math.max(0, value - Stat.BASE_VALUE);
        int soft = safeInt(SoloConfig.STAT_SOFT_CAP, 40);
        return invested <= soft ? invested : soft + (invested - soft) * 0.5;
    }

    public static double physicalDamage(PlayerData d) {
        return Math.min(CAP_PHYS, eff(d, Stat.STR) * safe(SoloConfig.STR_DAMAGE_PER_POINT, 0.006));
    }

    public static double moveSpeed(PlayerData d) {
        return Math.min(CAP_MOVE, eff(d, Stat.AGI) * safe(SoloConfig.AGI_SPEED_PER_POINT, 0.0025));
    }

    public static double attackSpeed(PlayerData d) {
        return Math.min(CAP_ATK_SPEED, eff(d, Stat.AGI) * 0.004);
    }

    public static double dodgeChance(PlayerData d) {
        return Math.min(CAP_DODGE, eff(d, Stat.AGI) * safe(SoloConfig.AGI_DODGE_PER_POINT, 0.0015));
    }

    public static double safeFall(PlayerData d) {
        return Math.min(3.0, eff(d, Stat.AGI) * 0.03);
    }

    public static double projectileDamage(PlayerData d) {
        return Math.min(CAP_PROJ, eff(d, Stat.PER) * safe(SoloConfig.PER_PROJECTILE_PER_POINT, 0.006));
    }

    public static double critChance(PlayerData d) {
        return Math.min(CAP_CRIT, eff(d, Stat.PER) * safe(SoloConfig.PER_CRIT_PER_POINT, 0.002));
    }

    public static double critDamage(PlayerData d) {
        return 0.5 + Math.min(0.3, eff(d, Stat.PER) * 0.003);
    }

    public static double bonusHealth(PlayerData d) {
        return Math.min(CAP_HEALTH, eff(d, Stat.VIT) * safe(SoloConfig.VIT_HEALTH_PER_POINT, 0.4));
    }

    public static double damageReduction(PlayerData d) {
        return Math.min(CAP_REDUCTION, eff(d, Stat.VIT) * safe(SoloConfig.VIT_REDUCTION_PER_POINT, 0.0015));
    }

    public static double debuffResist(PlayerData d) {
        return Math.min(CAP_DEBUFF, eff(d, Stat.VIT) * 0.002);
    }

    public static double magicPower(PlayerData d) {
        return Math.min(CAP_MAGIC, eff(d, Stat.INT) * safe(SoloConfig.INT_MAGIC_PER_POINT, 0.005));
    }

    public static double maxEnergy(PlayerData d) {
        return 100 + d.level * 0.5 + eff(d, Stat.INT) * 1.5 + d.skillRank("arcane_mind") * 15
                + (d.achievements.contains("magic_scholar") ? 20 : 0);
    }

    /** Восстановление энергии в секунду. */
    public static double energyRegen(PlayerData d) {
        double regen = 1.0 + Math.min(3.0, eff(d, Stat.INT) * 0.03);
        regen *= 1 + d.skillRank("arcane_mind") * 0.10;
        return regen;
    }

    public static double extraDropChance(PlayerData d) {
        return Math.min(CAP_DROP, eff(d, Stat.LUCK) * safe(SoloConfig.LUCK_DROP_PER_POINT, 0.002))
                + (d.achievements.contains("collector") ? 0.02 : 0);
    }

    public static double luckAttribute(PlayerData d) {
        return Math.min(CAP_LUCK_ATTR, eff(d, Stat.LUCK) * 0.05);
    }

    /** Смещение броска редкости оружия к лучшему качеству. */
    public static double qualityBonus(PlayerData d) {
        return Math.min(CAP_QUALITY, eff(d, Stat.LUCK) * 0.003);
    }

    /** Магическая сила базовых магических навыков системы (до бонуса INT). */
    public static double baseSpellDamage(PlayerData d) {
        return 4.0 + d.level * 0.12;
    }

    // Конфиг сервера доступен только в загруженном мире; эти обёртки защищают экран от ранних вызовов.
    private static double safe(net.neoforged.neoforge.common.ModConfigSpec.DoubleValue v, double def) {
        try { return v.get(); } catch (Exception e) { return def; }
    }

    private static int safeInt(net.neoforged.neoforge.common.ModConfigSpec.IntValue v, int def) {
        try { return v.get(); } catch (Exception e) { return def; }
    }
}
