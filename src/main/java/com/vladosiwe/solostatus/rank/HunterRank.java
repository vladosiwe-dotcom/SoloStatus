package com.vladosiwe.solostatus.rank;

import net.minecraft.network.chat.Component;

/** Ранги охотника в стиле Solo Leveling. Порог — очки силы (см. HunterRankSystem). */
public enum HunterRank {
    E("E", 0, 0xFF9E9E9E),
    D("D", 60, 0xFF8BC34A),
    C("C", 150, 0xFF4FC3F7),
    B("B", 260, 0xFF7E57C2),
    A("A", 400, 0xFFFFB300),
    S("S", 560, 0xFFFF3D3D),
    NATIONAL("N", 720, 0xFFFFFFFF);

    public static final HunterRank[] VALUES = values();

    public final String letter;
    public final int threshold;
    public final int color;

    HunterRank(String letter, int threshold, int color) {
        this.letter = letter;
        this.threshold = threshold;
        this.color = color;
    }

    public Component displayName() {
        return Component.translatable("solostatus.rank." + name().toLowerCase());
    }

    public static HunterRank byIndex(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : E;
    }
}
