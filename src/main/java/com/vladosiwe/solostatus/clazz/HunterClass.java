package com.vladosiwe.solostatus.clazz;

import com.vladosiwe.solostatus.stats.Stat;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Шесть основных классов. Выбираются при первом пробуждении системы. */
public enum HunterClass {
    WARRIOR("warrior", 0xFFE8503A, Stat.STR, Stat.VIT),
    THIEF("thief", 0xFF9B6BFF, Stat.AGI, Stat.PER),
    MAGE("mage", 0xFF4FA8FF, Stat.INT, Stat.PER),
    DEFENDER("defender", 0xFFC9C9C9, Stat.VIT, Stat.STR),
    ARCHER("archer", 0xFF63D46B, Stat.PER, Stat.AGI),
    SUPPORT("support", 0xFFFFE070, Stat.INT, Stat.VIT);

    public static final HunterClass[] VALUES = values();

    public final String key;
    public final int color;
    /** Стартовые бонусы класса: +3 к основной и +2 к вторичной характеристике. */
    public final Stat primary;
    public final Stat secondary;

    HunterClass(String key, int color, Stat primary, Stat secondary) {
        this.key = key;
        this.color = color;
        this.primary = primary;
        this.secondary = secondary;
    }

    public Component displayName() {
        return Component.translatable("solostatus.class." + key);
    }

    public Component description() {
        return Component.translatable("solostatus.class." + key + ".desc");
    }

    public Item icon() {
        return switch (this) {
            case WARRIOR -> Items.IRON_SWORD;
            case THIEF -> Items.SHEARS;
            case MAGE -> Items.BLAZE_ROD;
            case DEFENDER -> Items.SHIELD;
            case ARCHER -> Items.BOW;
            case SUPPORT -> Items.GOLDEN_APPLE;
        };
    }

    public Specialization[] specializations() {
        return switch (this) {
            case WARRIOR -> new Specialization[]{Specialization.BERSERKER, Specialization.WARLORD};
            case THIEF -> new Specialization[]{Specialization.SHADOW, Specialization.BLADE_DANCER};
            case MAGE -> new Specialization[]{Specialization.ELEMENTALIST, Specialization.ARCANIST};
            case DEFENDER -> new Specialization[]{Specialization.GUARDIAN, Specialization.JUGGERNAUT};
            case ARCHER -> new Specialization[]{Specialization.SHARPSHOOTER, Specialization.RANGER};
            case SUPPORT -> new Specialization[]{Specialization.CLERIC, Specialization.ENCHANTER};
        };
    }

    public static HunterClass byIndex(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : null;
    }
}
