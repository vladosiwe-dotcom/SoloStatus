package com.vladosiwe.solostatus.skill;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Анимации Epic Fight для активных навыков (имена полей yesman.epicfight.gameasset.Animations,
 * сверены с Epic Fight 21.17.3.1). Анимации только визуальные: урон считает навык системы.
 * Магические навыки и выстрелы без подходящей двуногой анимации не указаны — у них ванильный взмах.
 */
public final class SkillAnimations {
    private static final Map<String, String> MAP = Map.ofEntries(
            // Воин
            Map.entry("power_strike", "SWORD_DASH"),
            Map.entry("whirlwind", "STEEL_WHIRLWIND"),
            Map.entry("charge", "SPEAR_DASH"),
            Map.entry("devastating_blow", "METEOR_SLAM"),
            Map.entry("titan_slash", "THE_GUILLOTINE"),
            Map.entry("berserk", "BIPED_FRUSTRATED"),
            Map.entry("earthbreaker", "METEOR_SLAM"),
            // Убийца
            Map.entry("quick_dash", "BIPED_STEP_FORWARD"),
            Map.entry("flurry", "DAGGER_DUAL_AUTO4"),
            Map.entry("assassinate", "EVISCERATE_SECOND"),
            Map.entry("thousand_cuts", "RELENTLESS_COMBO"),
            Map.entry("shadow_step", "BLADE_RUSH_COMBO1"),
            Map.entry("blade_storm", "STEEL_WHIRLWIND"),
            // Защитник
            Map.entry("shield_bash", "FIST_DASH"),
            Map.entry("seismic_charge", "SPEAR_DASH"),
            // Лучник
            Map.entry("power_shot", "BIPED_BOW_SHOT"),
            Map.entry("arrow_volley", "BIPED_BOW_SHOT"),
            Map.entry("knockback_shot", "BIPED_BOW_SHOT"),
            Map.entry("sniper_shot", "BIPED_CROSSBOW_SHOT"),
            Map.entry("evasive_roll", "BIPED_ROLL_BACKWARD"),
            // Пробуждения
            Map.entry("cross_slash", "DANCING_EDGE"),
            Map.entry("blade_flash", "BATTOJUTSU_DASH"),
            Map.entry("sunder", "AXE_DASH"),
            Map.entry("piercing_thrust", "HEARTPIERCER"),
            Map.entry("ground_pound", "METEOR_SLAM"),
            Map.entry("fist_barrage", "FIST_AUTO3"),
            Map.entry("frost_arrow", "BIPED_BOW_SHOT"),
            Map.entry("explosive_bolt", "BIPED_CROSSBOW_SHOT"),
            Map.entry("vital_strike", "SHARP_STAB"));

    private SkillAnimations() {}

    @Nullable
    public static String forSkill(String skillId) {
        return MAP.get(skillId);
    }

    /** Все имена анимаций Epic Fight, которые используют навыки. */
    public static java.util.Collection<String> allAnimations() {
        return new java.util.TreeSet<>(MAP.values());
    }
}
