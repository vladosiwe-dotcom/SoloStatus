package com.vladosiwe.solostatus.combat;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Типы оружия, по которым ведётся Mastery и адаптивное пробуждение навыков. */
public enum WeaponType {
    NONE("none", false, Items.BARRIER),
    FIST("fist", true, Items.LEATHER),
    SWORD("sword", true, Items.IRON_SWORD),
    GREATSWORD("greatsword", true, Items.NETHERITE_SWORD),
    DAGGER("dagger", true, Items.SHEARS),
    AXE("axe", true, Items.IRON_AXE),
    SPEAR("spear", true, Items.TRIDENT),
    MACE("mace", true, Items.MACE),
    BOW("bow", false, Items.BOW),
    CROSSBOW("crossbow", false, Items.CROSSBOW),
    SHIELD("shield", false, Items.SHIELD),
    STAFF("staff", false, Items.BLAZE_ROD);

    public static final WeaponType[] VALUES = values();

    public final String key;
    public final boolean melee;
    public final Item icon;

    WeaponType(String key, boolean melee, Item icon) {
        this.key = key;
        this.melee = melee;
        this.icon = icon;
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
