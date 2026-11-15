package com.vladosiwe.solostatus.clazz;

import com.vladosiwe.solostatus.combat.WeaponType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * How well a class handles a weapon type. A Warrior swings a dagger slower and weaker than a Thief does: the weapon
 * is not his. Applied to melee and ranged damage, to the attack speed of the weapon in the main hand (which also
 * speeds up or slows down Epic Fight animations) and to how fast mastery grows.
 */
public enum Proficiency {
    MAIN("main", 0.10, 0.10, 1.15, ChatFormatting.GOLD),
    FAMILIAR("familiar", 0.0, 0.0, 1.0, ChatFormatting.GREEN),
    UNFAMILIAR("unfamiliar", -0.10, -0.10, 0.85, ChatFormatting.YELLOW),
    FOREIGN("foreign", -0.25, -0.25, 0.6, ChatFormatting.RED);

    public final String key;
    /** Added to the damage multiplier (+0.10 = +10%). */
    public final double damage;
    /** Attack speed modifier, multiplied with the base (−0.25 = 25% slower). */
    public final double attackSpeed;
    /** Multiplier of mastery gained with this weapon. */
    public final double masteryGain;
    public final ChatFormatting color;

    Proficiency(String key, double damage, double attackSpeed, double masteryGain, ChatFormatting color) {
        this.key = key;
        this.damage = damage;
        this.attackSpeed = attackSpeed;
        this.masteryGain = masteryGain;
        this.color = color;
    }

    public Component displayName() {
        return Component.translatable("solostatus.proficiency." + key).withStyle(color);
    }

    /** With the specialization: a Spellblade fights with swords as its own weapon. */
    public static Proficiency of(com.vladosiwe.solostatus.data.PlayerData d, WeaponType t) {
        if (d.specialization == Specialization.SPELLBLADE && (t == WeaponType.SWORD || t == WeaponType.STAFF)) return MAIN;
        if (d.specialization == Specialization.DUELIST && t == WeaponType.DAGGER) return UNFAMILIAR; // a parrying dagger
        return of(d.hunterClass, t);
    }

    /** Before the System awakens (no class) every weapon is familiar: no bonus, no penalty. */
    public static Proficiency of(HunterClass c, WeaponType t) {
        if (c == null || !t.hasMastery()) return FAMILIAR;
        return switch (c) {
            case WARRIOR -> switch (t) {
                case SWORD, GREATSWORD, AXE, MACE -> MAIN;
                case SPEAR, SHIELD -> FAMILIAR;
                case BOW, CROSSBOW -> UNFAMILIAR;
                default -> FOREIGN; // dagger, staff
            };
            case THIEF -> switch (t) {
                case DAGGER -> MAIN;
                case SWORD, BOW, CROSSBOW -> FAMILIAR;
                case SPEAR, AXE -> UNFAMILIAR;
                default -> FOREIGN; // greatsword, mace, staff, shield
            };
            case MAGE -> switch (t) {
                case STAFF -> MAIN;
                case DAGGER -> FAMILIAR;
                case SWORD -> UNFAMILIAR;
                default -> FOREIGN;
            };
            case DEFENDER -> switch (t) {
                case SHIELD, MACE, SWORD -> MAIN;
                case SPEAR, AXE -> FAMILIAR;
                case GREATSWORD, CROSSBOW -> UNFAMILIAR;
                default -> FOREIGN; // dagger, bow, staff
            };
            case ARCHER -> switch (t) {
                case BOW, CROSSBOW -> MAIN;
                case DAGGER -> FAMILIAR;
                case SWORD, SPEAR -> UNFAMILIAR;
                default -> FOREIGN;
            };
            case SUPPORT -> switch (t) {
                case STAFF, MACE -> MAIN;
                case SHIELD -> FAMILIAR;
                case SWORD, CROSSBOW -> UNFAMILIAR;
                default -> FOREIGN;
            };
        };
    }
}
