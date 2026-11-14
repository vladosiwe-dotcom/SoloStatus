package com.vladosiwe.solostatus.combat;

import net.minecraft.network.chat.Component;

/** Типы оружия, по которым ведётся Mastery и адаптивное пробуждение навыков. */
public enum WeaponType {
    NONE("none", false),
    FIST("fist", true),
    SWORD("sword", true),
    GREATSWORD("greatsword", true),
    DAGGER("dagger", true),
    AXE("axe", true),
    SPEAR("spear", true),
    MACE("mace", true),
    BOW("bow", false),
    CROSSBOW("crossbow", false),
    SHIELD("shield", false),
    STAFF("staff", false);

    public static final WeaponType[] VALUES = values();

    public final String key;
    public final boolean melee;

    WeaponType(String key, boolean melee) {
        this.key = key;
        this.melee = melee;
    }

    public boolean ranged() {
        return this == BOW || this == CROSSBOW;
    }

    /** Unarmed fighting is not part of the System: an empty hand has no mastery, skills or bonuses. */
    public boolean hasMastery() {
        return this != NONE && this != FIST;
    }

    public Component displayName() {
        return Component.translatable("solostatus.weapon." + key);
    }

    public static WeaponType byKey(String key) {
        for (WeaponType t : VALUES) if (t.key.equals(key)) return t;
        return NONE;
    }
}
