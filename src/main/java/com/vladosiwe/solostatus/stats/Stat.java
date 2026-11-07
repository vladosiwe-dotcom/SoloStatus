package com.vladosiwe.solostatus.stats;

import net.minecraft.network.chat.Component;

/** Шесть основных характеристик охотника. */
public enum Stat {
    STR("str", 0xFFFF5A5A),
    AGI("agi", 0xFF5AFF8C),
    PER("per", 0xFFFFD25A),
    VIT("vit", 0xFFFF8CC8),
    INT("int", 0xFF6EA0FF),
    LUCK("luck", 0xFFC88CFF);

    public static final int BASE_VALUE = 10;
    public static final Stat[] VALUES = values();

    public final String key;
    public final int color;

    Stat(String key, int color) {
        this.key = key;
        this.color = color;
    }

    public Component shortName() {
        return Component.translatable("solostatus.stat." + key);
    }

    public Component fullName() {
        return Component.translatable("solostatus.stat." + key + ".full");
    }

    public Component description() {
        return Component.translatable("solostatus.stat." + key + ".desc");
    }

    public static Stat byIndex(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : null;
    }
}
