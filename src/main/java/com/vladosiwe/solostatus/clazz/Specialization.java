package com.vladosiwe.solostatus.clazz;

import net.minecraft.network.chat.Component;

/**
 * Двенадцать специализаций (по две на класс). Выбор — на уровне specializationLevel (по умолчанию 5).
 * Вторая ступень («Смена пути») открывается Испытанием класса начиная с advancementLevel (по умолчанию 40).
 */
public enum Specialization {
    BERSERKER("berserker", HunterClass.WARRIOR),
    WARLORD("warlord", HunterClass.WARRIOR),
    SHADOW("shadow", HunterClass.THIEF),
    BLADE_DANCER("blade_dancer", HunterClass.THIEF),
    ELEMENTALIST("elementalist", HunterClass.MAGE),
    ARCANIST("arcanist", HunterClass.MAGE),
    GUARDIAN("guardian", HunterClass.DEFENDER),
    JUGGERNAUT("juggernaut", HunterClass.DEFENDER),
    SHARPSHOOTER("sharpshooter", HunterClass.ARCHER),
    RANGER("ranger", HunterClass.ARCHER),
    CLERIC("cleric", HunterClass.SUPPORT),
    ENCHANTER("enchanter", HunterClass.SUPPORT);

    public static final Specialization[] VALUES = values();

    public final String key;
    public final HunterClass parent;

    Specialization(String key, HunterClass parent) {
        this.key = key;
        this.parent = parent;
    }

    public Component displayName() {
        return Component.translatable("solostatus.spec." + key);
    }

    public Component advancedName() {
        return Component.translatable("solostatus.spec." + key + ".advanced");
    }

    public Component description() {
        return Component.translatable("solostatus.spec." + key + ".desc");
    }

    public static Specialization byIndex(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : null;
    }
}
