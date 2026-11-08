package com.vladosiwe.solostatus.accessory;

import net.minecraft.network.chat.Component;

/**
 * Bonuses a piece of jewellery can roll. min/max are the values of one roll (a rarity picks where in the range
 * it lands), cap limits the total of all worn pieces so stacking jewellery cannot break the stat caps.
 */
public enum Affix {
    MAX_HEALTH("max_health", 1, 6, 12, false, true, true),
    ARMOR("armor", 0.5, 3, 6, false, true, false),
    MOVE_SPEED("move_speed", 0.01, 0.05, 0.10, true, true, true),
    ATTACK_SPEED("attack_speed", 0.01, 0.06, 0.10, true, true, false),
    DAMAGE("damage", 0.01, 0.06, 0.12, true, true, false),
    SPELL_POWER("spell_power", 0.02, 0.10, 0.15, true, true, true),
    CRIT_CHANCE("crit_chance", 0.01, 0.05, 0.08, true, true, false),
    CRIT_DAMAGE("crit_damage", 0.03, 0.20, 0.30, true, true, true),
    DODGE("dodge", 0.01, 0.04, 0.06, true, false, true),
    LIFESTEAL("lifesteal", 0.01, 0.04, 0.05, true, false, true),
    MAX_ENERGY("max_energy", 5, 30, 50, false, true, true),
    ENERGY_REGEN("energy_regen", 0.04, 0.20, 0.30, true, false, true),
    DEBUFF_RESIST("debuff_resist", 0.02, 0.10, 0.15, true, false, true),
    XP_GAIN("xp_gain", 0.02, 0.08, 0.10, true, false, true),
    MASTERY_GAIN("mastery_gain", 0.02, 0.08, 0.10, true, false, true),
    LUCK("luck", 0.25, 1.5, 2, false, true, false);

    public static final Affix[] VALUES = values();

    public final String key;
    public final double min, max, cap;
    public final boolean percent;
    private final boolean ring, amulet;

    Affix(String key, double min, double max, double cap, boolean percent, boolean ring, boolean amulet) {
        this.key = key; this.min = min; this.max = max; this.cap = cap; this.percent = percent; this.ring = ring; this.amulet = amulet;
    }

    public boolean allowedOn(AccessoryKind kind) {
        return kind == AccessoryKind.RING ? ring : amulet;
    }

    public Component displayName() {
        return Component.translatable("solostatus.affix." + key);
    }

    /** "+4%" or "+3.5". */
    public String format(double value) {
        if (percent) return "+" + Math.round(value * 1000) / 10.0 + "%";
        double r = Math.round(value * 10) / 10.0;
        return "+" + (r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r));
    }

    public static Affix byKey(String key) {
        for (Affix a : VALUES) if (a.key.equals(key)) return a;
        return null;
    }
}
