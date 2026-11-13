package com.vladosiwe.solostatus.quality;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Rarity;

/**
 * Качество экземпляра оружия. Для совместимости с Legendary Tooltips и ванильным цветом названия
 * дополнительно выставляется ванильный компонент minecraft:rarity.
 */
public enum QualityRarity {
    COMMON("common", ChatFormatting.WHITE, 0xFFE0E0E0, Rarity.COMMON, -0.05f, 0.05f),
    UNCOMMON("uncommon", ChatFormatting.GREEN, 0xFF55FF55, Rarity.UNCOMMON, 0.03f, 0.10f),
    RARE("rare", ChatFormatting.AQUA, 0xFF55C8FF, Rarity.RARE, 0.08f, 0.16f),
    EPIC("epic", ChatFormatting.LIGHT_PURPLE, 0xFFC864FF, Rarity.EPIC, 0.14f, 0.24f),
    LEGENDARY("legendary", ChatFormatting.GOLD, 0xFFFFAA00, Rarity.EPIC, 0.22f, 0.35f);

    public static final QualityRarity[] VALUES = values();

    public final String key;
    public final ChatFormatting format;
    public final int color;
    public final Rarity vanilla;
    /** Диапазон бонуса урона (доля), из которого случайно выбирается значение экземпляра. */
    public final float minDamage;
    public final float maxDamage;

    QualityRarity(String key, ChatFormatting format, int color, Rarity vanilla, float minDamage, float maxDamage) {
        this.key = key;
        this.format = format;
        this.color = color;
        this.vanilla = vanilla;
        this.minDamage = minDamage;
        this.maxDamage = maxDamage;
    }

    public Component displayName() {
        return Component.translatable("solostatus.rarity." + key).withStyle(format);
    }

    public static QualityRarity byIndex(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : COMMON;
    }
}
