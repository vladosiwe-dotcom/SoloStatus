package com.vladosiwe.solostatus.registry;

import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.combat.WeaponType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * The System's own icons (weapon types, magic, classes), drawn by tools/icons/make_icons.py.
 * Each one is the hidden item solostatus:system_icon with a custom_model_data number, so it renders anywhere an item
 * does: the System window, notices, tooltips. The order must match ICONS in make_icons.py (number = ordinal + 1).
 */
public enum SystemIcon {
    WEAPON_SWORD, WEAPON_GREATSWORD, WEAPON_DAGGER, WEAPON_AXE, WEAPON_MACE, WEAPON_SPEAR,
    WEAPON_BOW, WEAPON_CROSSBOW, WEAPON_STAFF, WEAPON_SHIELD,
    MAGIC,
    CLASS_WARRIOR, CLASS_THIEF, CLASS_MAGE, CLASS_DEFENDER, CLASS_ARCHER, CLASS_SUPPORT;

    public ItemStack stack() {
        ItemStack st = new ItemStack(ModItems.SYSTEM_ICON.get());
        st.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(ordinal() + 1));
        return st;
    }

    public static ItemStack of(WeaponType type) {
        return switch (type) {
            case SWORD -> WEAPON_SWORD.stack();
            case GREATSWORD -> WEAPON_GREATSWORD.stack();
            case DAGGER -> WEAPON_DAGGER.stack();
            case AXE -> WEAPON_AXE.stack();
            case MACE -> WEAPON_MACE.stack();
            case SPEAR -> WEAPON_SPEAR.stack();
            case BOW -> WEAPON_BOW.stack();
            case CROSSBOW -> WEAPON_CROSSBOW.stack();
            case STAFF -> WEAPON_STAFF.stack();
            case SHIELD -> WEAPON_SHIELD.stack();
            case NONE, FIST -> ItemStack.EMPTY;
        };
    }

    public static ItemStack of(HunterClass c) {
        return switch (c) {
            case WARRIOR -> CLASS_WARRIOR.stack();
            case THIEF -> CLASS_THIEF.stack();
            case MAGE -> CLASS_MAGE.stack();
            case DEFENDER -> CLASS_DEFENDER.stack();
            case ARCHER -> CLASS_ARCHER.stack();
            case SUPPORT -> CLASS_SUPPORT.stack();
        };
    }

    /** Icon for a mastery key: a weapon type, or "magic:<school>". */
    public static ItemStack mastery(String key) {
        return key.startsWith("magic:") ? MAGIC.stack() : of(WeaponType.byKey(key));
    }
}
