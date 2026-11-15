package com.vladosiwe.solostatus.skill;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Анимации Epic Fight для активных навыков (имена полей yesman.epicfight.gameasset.Animations,
 * сверены с Epic Fight 21.17.3.1). Анимации только визуальные: урон считает навык системы.
 * У каждого активного навыка своя анимация; заклинания Мага анимирует сам Iron's Spells.
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
            Map.entry("frost_arrow", "BIPED_BOW_SHOT"),
            Map.entry("explosive_bolt", "BIPED_CROSSBOW_SHOT"),
            Map.entry("vital_strike", "SHARP_STAB"),
            // Воин, Убийца, Защитник, Лучник, Поддержка — без своей анимации раньше. Только разовые анимации:
            // стойки защиты и прицеливание в Epic Fight зациклены и оставили бы героя в позе.
            Map.entry("war_cry", "EVERLASTING_ALLEGIANCE_CALL"),
            Map.entry("vanish", "BIPED_STEP_BACKWARD"),
            Map.entry("poison_blade", "DAGGER_AUTO3"),
            Map.entry("taunt", "BIPED_WAVE_HAND"),
            Map.entry("defensive_stance", "SWORD_GUARD_ACTIVE_HIT1"),
            Map.entry("counter_attack", "SWORD_GUARD_ACTIVE_HIT2"),
            Map.entry("fortress", "SWORD_GUARD_ACTIVE_HIT3"),
            Map.entry("rain_of_arrows", "BIPED_BOW_SHOT"),
            Map.entry("hunters_mark", "BIPED_WAVE_HAND"),
            Map.entry("mend", "EVERLASTING_ALLEGIANCE_CALL"),
            Map.entry("blessing", "BIPED_SALUTE"),
            Map.entry("barrier", "LONGSWORD_GUARD_ACTIVE_HIT1"),
            Map.entry("purify", "EVERLASTING_ALLEGIANCE_CALL"),
            Map.entry("sanctuary", "WRATHFUL_LIGHTING"),
            // Общие навыки и пробуждения
            Map.entry("sprint", "BIPED_STEP_FORWARD"),
            Map.entry("bloodlust", "BIPED_FRUSTRATED"),
            Map.entry("arcane_blast", "BIPED_JAVELIN_THROW"),
            Map.entry("mana_blade", "SWEEPING_EDGE"),
            Map.entry("parry", "SWORD_GUARD_ACTIVE_HIT1"),
            Map.entry("body_reinforcement", "BIPED_FRUSTRATED"),
            Map.entry("air_step", "BIPED_PHANTOM_ASCENT_FORWARD"),
            Map.entry("focus", "BIPED_SALUTE"),
            Map.entry("thought_acceleration", "BIPED_STEP_LEFT"),
            Map.entry("overwhelming_presence", "WRATHFUL_LIGHTING"),
            Map.entry("shadow_legion", "EVERLASTING_ALLEGIANCE_CALL"),
            Map.entry("ruler_grip", "EVERLASTING_ALLEGIANCE_CATCH"),
            // Специализации и высшие умения
            Map.entry("meteor", "WRATHFUL_LIGHTING"),
            Map.entry("gravity_well", "EVERLASTING_ALLEGIANCE_CALL"),
            Map.entry("aegis", "GREATSWORD_GUARD_HIT"),
            Map.entry("holy_light", "WRATHFUL_LIGHTING"),
            Map.entry("hex", "BIPED_JAVELIN_THROW"),
            Map.entry("arcane_cataclysm", "WRATHFUL_LIGHTING"),
            Map.entry("unbreakable", "LONGSWORD_GUARD_ACTIVE_HIT2"),
            Map.entry("storm_of_arrows", "BIPED_BOW_SHOT"),
            Map.entry("divine_intervention", "WRATHFUL_LIGHTING"),
            // третьи специализации
            Map.entry("duel_challenge", "BIPED_SALUTE"),
            Map.entry("toxic_cloud", "EVERLASTING_ALLEGIANCE_CALL"),
            Map.entry("arcane_strike", "SWEEPING_EDGE"),
            Map.entry("retribution", "METEOR_SLAM"),
            Map.entry("bolt_barrage", "BIPED_CROSSBOW_SHOT"),
            Map.entry("battle_hymn", "EVERLASTING_ALLEGIANCE_CALL"),
            // цепочки и Сокрытие
            Map.entry("riposte", "EVISCERATE_FIRST"),
            Map.entry("sky_strike", "METEOR_SLAM"),
            Map.entry("concealment", "BIPED_STEP_BACKWARD"));

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
