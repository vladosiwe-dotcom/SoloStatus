package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.clazz.Specialization;
import com.vladosiwe.solostatus.combat.WeaponType;
import com.vladosiwe.solostatus.stats.Stat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.vladosiwe.solostatus.skill.SkillCategory.*;
import static com.vladosiwe.solostatus.skill.SkillSource.*;

/**
 * Реестр всех навыков. Порядок регистрации = порядок отображения.
 *
 * Структура (подробно — docs/DESIGN.md):
 *  - 6 классов × 6 навыков (2 стартовых бесплатно + 4 за очки навыков и условия);
 *  - 12 специализаций × 2 навыка (активный + пассивный);
 *  - 6 высших умений (по одному на класс, вариант зависит от специализации) — Испытание смены пути;
 *  - навыки пробуждения — открываются боевым опытом любым классом;
 *  - скрытые навыки — условия неизвестны до пробуждения.
 */
public final class Skills {
    private static final Map<String, SkillDef> BY_ID = new LinkedHashMap<>();

    private Skills() {}

    private static SkillDef reg(SkillDef def) {
        if (BY_ID.put(def.id, def) != null) throw new IllegalStateException("Повторный id навыка: " + def.id);
        return def;
    }

    private static SkillDef.Builder b(String id, SkillSource src, SkillCategory cat, Item icon) {
        return SkillDef.builder(id, src, cat, icon);
    }

    @Nullable
    public static SkillDef get(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static Collection<SkillDef> all() {
        return BY_ID.values();
    }

    public static List<SkillDef> forClass(HunterClass c) {
        return BY_ID.values().stream().filter(d -> d.hunterClass == c && d.spec == null && d.source != SkillSource.ULTIMATE).toList();
    }

    public static List<SkillDef> forSpec(Specialization s) {
        return BY_ID.values().stream().filter(d -> d.spec == s).toList();
    }

    @Nullable
    public static SkillDef ultimate(HunterClass c) {
        return BY_ID.values().stream().filter(d -> d.source == SkillSource.ULTIMATE && d.hunterClass == c).findFirst().orElse(null);
    }

    public static List<SkillDef> awakenings() {
        return BY_ID.values().stream().filter(d -> d.source == AWAKENING || d.source == HIDDEN).toList();
    }

    static {
        // ============================== WARRIOR ==============================
        HunterClass W = HunterClass.WARRIOR;
        reg(b("power_strike", CLASS_START, ATTACK, Items.IRON_SWORD).forClass(W).cooldown(6, 0.5).energy(10).tree(0, 0).effect(SkillEffects::powerStrike).build());
        reg(b("warrior_spirit", CLASS_START, PASSIVE, Items.RED_DYE).forClass(W).tree(0, 2).build());
        reg(b("whirlwind", CLASS, ATTACK, Items.IRON_AXE).forClass(W).level(5).parent("power_strike", 2).cooldown(10, 0.6).energy(20).tree(1, 0).effect(SkillEffects::whirlwind).build());
        reg(b("charge", CLASS, MOVEMENT, Items.LEATHER_BOOTS).forClass(W).level(10).cooldown(12, 0.8).energy(15).tree(1, 1).effect(SkillEffects::charge).build());
        reg(b("war_cry", CLASS, SUPPORT, Items.GOAT_HORN).forClass(W).level(15).parent("warrior_spirit", 2).cooldown(30, 2).energy(25).tree(1, 2).effect(SkillEffects::warCry).build());
        reg(b("devastating_blow", CLASS, ATTACK, Items.NETHERITE_AXE).forClass(W).level(20).parent("whirlwind", 2).cooldown(25, 1.5).energy(35).tree(2, 0).effect(SkillEffects::devastatingBlow).build());

        // ============================== THIEF ==============================
        HunterClass T = HunterClass.THIEF;
        reg(b("quick_dash", CLASS_START, MOVEMENT, Items.FEATHER).forClass(T).cooldown(5, 0.4).energy(8).tree(0, 0).effect(SkillEffects::quickDash).build());
        reg(b("ambush", CLASS_START, PASSIVE, Items.SHEARS).forClass(T).tree(0, 2).build());
        reg(b("flurry", CLASS, ATTACK, Items.IRON_SWORD).forClass(T).level(5).parent("ambush", 2).cooldown(9, 0.6).energy(18).tree(1, 2).effect(SkillEffects::flurry).build());
        reg(b("vanish", CLASS, DEFENSE, Items.GLASS_PANE).forClass(T).level(10).parent("quick_dash", 2).cooldown(30, 2).energy(25).tree(1, 0).effect(SkillEffects::vanish).build());
        reg(b("poison_blade", CLASS, ATTACK, Items.SPIDER_EYE).forClass(T).level(15).cooldown(25, 1.5).energy(20).tree(1, 1).effect(SkillEffects::poisonBlade).build());
        reg(b("assassinate", CLASS, ATTACK, Items.WITHER_SKELETON_SKULL).forClass(T).level(20).parent("flurry", 2).cooldown(20, 1.2).energy(30).tree(2, 2).effect(SkillEffects::assassinate).build());

        // ============================== MAGE ==============================
        HunterClass M = HunterClass.MAGE;
        reg(b("mana_bolt", CLASS_START, MAGIC, Items.AMETHYST_SHARD).forClass(M).cooldown(2.5, 0.15).energy(12).tree(0, 0).effect(SkillEffects::manaBolt).build());
        reg(b("arcane_mind", CLASS_START, PASSIVE, Items.LAPIS_LAZULI).forClass(M).tree(0, 2).build());
        reg(b("frost_nova", CLASS, MAGIC, Items.BLUE_ICE).forClass(M).level(5).parent("mana_bolt", 2).cooldown(12, 0.8).energy(25).tree(1, 0).effect(SkillEffects::frostNova).build());
        reg(b("flame_burst", CLASS, MAGIC, Items.FIRE_CHARGE).forClass(M).level(10).parent("mana_bolt", 2).cooldown(10, 0.6).energy(25).tree(1, 1).effect(SkillEffects::flameBurst).build());
        reg(b("chain_lightning", CLASS, MAGIC, Items.LIGHTNING_ROD).forClass(M).level(15).parent("flame_burst", 2).cooldown(14, 0.8).energy(30).tree(2, 1).effect(SkillEffects::chainLightning).build());
        reg(b("mana_shield", CLASS, DEFENSE, Items.PRISMARINE_CRYSTALS).forClass(M).level(20).parent("arcane_mind", 2).cooldown(30, 2).energy(35).tree(1, 2).effect(SkillEffects::manaShield).build());

        // ============================== DEFENDER ==============================
        HunterClass D = HunterClass.DEFENDER;
        reg(b("shield_bash", CLASS_START, ATTACK, Items.SHIELD).forClass(D).cooldown(6, 0.4).energy(10).tree(0, 0).effect(SkillEffects::shieldBash).build());
        reg(b("iron_skin", CLASS_START, PASSIVE, Items.IRON_CHESTPLATE).forClass(D).tree(0, 2).build());
        reg(b("taunt", CLASS, DEFENSE, Items.BELL).forClass(D).level(5).cooldown(15, 1).energy(15).tree(1, 0).effect(SkillEffects::taunt).build());
        reg(b("defensive_stance", CLASS, DEFENSE, Items.OBSIDIAN).forClass(D).level(10).parent("iron_skin", 2).cooldown(25, 1.5).energy(20).tree(1, 2).effect(SkillEffects::defensiveStance).build());
        reg(b("counter_attack", CLASS, DEFENSE, Items.CACTUS).forClass(D).level(15).parent("shield_bash", 2).cooldown(15, 1).energy(20).tree(1, 1).effect(SkillEffects::counterAttack).build());
        reg(b("fortress", CLASS, SUPPORT, Items.IRON_BLOCK).forClass(D).level(20).parent("defensive_stance", 2).cooldown(40, 2).energy(40).tree(2, 2).effect(SkillEffects::fortress).build());

        // ============================== ARCHER ==============================
        HunterClass A = HunterClass.ARCHER;
        reg(b("power_shot", CLASS_START, ATTACK, Items.SPECTRAL_ARROW).forClass(A).cooldown(5, 0.3).energy(12).tree(0, 0).effect(SkillEffects::powerShot).build());
        reg(b("steady_aim", CLASS_START, PASSIVE, Items.TARGET).forClass(A).tree(0, 2).build());
        reg(b("arrow_volley", CLASS, ATTACK, Items.ARROW).forClass(A).level(5).parent("power_shot", 2).cooldown(10, 0.6).energy(20).tree(1, 0).effect(SkillEffects::arrowVolley).build());
        reg(b("knockback_shot", CLASS, ATTACK, Items.SLIME_BALL).forClass(A).level(10).cooldown(8, 0.5).energy(12).tree(1, 1).effect(SkillEffects::knockbackShot).build());
        reg(b("rain_of_arrows", CLASS, ATTACK, Items.TIPPED_ARROW).forClass(A).level(15).parent("arrow_volley", 2).cooldown(18, 1).energy(30).tree(2, 0).effect(SkillEffects::rainOfArrows).build());
        reg(b("hunters_mark", CLASS, SUPPORT, Items.SPYGLASS).forClass(A).level(20).parent("steady_aim", 2).cooldown(20, 1).energy(15).tree(1, 2).effect(SkillEffects::huntersMark).build());

        // ============================== SUPPORT ==============================
        HunterClass S = HunterClass.SUPPORT;
        reg(b("mend", CLASS_START, SUPPORT, Items.GLISTERING_MELON_SLICE).forClass(S).cooldown(6, 0.4).energy(15).tree(0, 0).effect(SkillEffects::mend).build());
        reg(b("aura_of_vigor", CLASS_START, PASSIVE, Items.GOLDEN_CARROT).forClass(S).tree(0, 2).build());
        reg(b("blessing", CLASS, SUPPORT, Items.GOLDEN_APPLE).forClass(S).level(5).parent("mend", 2).cooldown(25, 1.5).energy(20).tree(1, 0).effect(SkillEffects::blessing).build());
        reg(b("barrier", CLASS, DEFENSE, Items.HONEYCOMB).forClass(S).level(10).cooldown(25, 1.5).energy(25).tree(1, 1).effect(SkillEffects::barrier).build());
        reg(b("purify", CLASS, SUPPORT, Items.MILK_BUCKET).forClass(S).level(15).parent("aura_of_vigor", 2).cooldown(20, 1).energy(20).tree(1, 2).effect(SkillEffects::purify).build());
        reg(b("sanctuary", CLASS, SUPPORT, Items.BEACON).forClass(S).level(20).parent("blessing", 2).cooldown(40, 2).energy(40).tree(2, 0).effect(SkillEffects::sanctuary).build());

        // ============================== SPECIALIZATIONS ==============================
        spec(Specialization.BERSERKER, "berserk", Items.REDSTONE, SkillEffects::berserk, 40, 2, 30, "blood_rage", Items.FERMENTED_SPIDER_EYE);
        spec(Specialization.WARLORD, "earthbreaker", Items.MACE, SkillEffects::earthbreaker, 16, 1, 25, "commander", Items.WHITE_BANNER);
        spec(Specialization.SHADOW, "shadow_step", Items.ENDER_PEARL, SkillEffects::shadowStep, 10, 0.6, 18, "shadow_veil", Items.BLACK_DYE);
        spec(Specialization.BLADE_DANCER, "blade_storm", Items.DIAMOND_SWORD, SkillEffects::bladeStorm, 14, 0.8, 25, "blade_rhythm", Items.MUSIC_DISC_CAT);
        spec(Specialization.ELEMENTALIST, "meteor", Items.MAGMA_BLOCK, SkillEffects::meteor, 20, 1.2, 40, "elemental_attunement", Items.BLAZE_POWDER);
        spec(Specialization.ARCANIST, "gravity_well", Items.ENDER_EYE, SkillEffects::gravityWell, 18, 1, 30, "arcane_flow", Items.EXPERIENCE_BOTTLE);
        spec(Specialization.GUARDIAN, "aegis", Items.TOTEM_OF_UNDYING, SkillEffects::aegis, 35, 2, 35, "bulwark", Items.NETHERITE_CHESTPLATE);
        spec(Specialization.JUGGERNAUT, "seismic_charge", Items.ANVIL, SkillEffects::seismicCharge, 14, 0.8, 20, "thorns_body", Items.SWEET_BERRIES);
        spec(Specialization.SHARPSHOOTER, "sniper_shot", Items.CROSSBOW, SkillEffects::sniperShot, 12, 0.8, 22, "deadeye", Items.ENDER_EYE);
        spec(Specialization.RANGER, "evasive_roll", Items.RABBIT_FOOT, SkillEffects::evasiveRoll, 8, 0.5, 15, "fleet_foot", Items.LEATHER_BOOTS);
        spec(Specialization.CLERIC, "holy_light", Items.GLOWSTONE_DUST, SkillEffects::holyLight, 20, 1.2, 35, "divine_grace", Items.ENCHANTED_GOLDEN_APPLE);
        spec(Specialization.ENCHANTER, "hex", Items.FERMENTED_SPIDER_EYE, SkillEffects::hex, 14, 0.8, 18, "resonance", Items.ENCHANTED_BOOK);

        // ============================== ULTIMATES (Испытание смены пути) ==============================
        ult(W, "titan_slash", Items.NETHERITE_SWORD, SkillEffects::titanSlash);
        ult(T, "thousand_cuts", Items.ECHO_SHARD, SkillEffects::thousandCuts);
        ult(M, "arcane_cataclysm", Items.NETHER_STAR, SkillEffects::arcaneCataclysm);
        ult(D, "unbreakable", Items.NETHERITE_BLOCK, SkillEffects::unbreakable);
        ult(A, "storm_of_arrows", Items.TRIDENT, SkillEffects::stormOfArrows);
        ult(S, "divine_intervention", Items.TOTEM_OF_UNDYING, SkillEffects::divineIntervention);

        // ============================== AWAKENINGS (боевой опыт, любой класс) ==============================
        reg(b("sprint", AWAKENING, MOVEMENT, Items.SUGAR).level(3).requires(Req.counter("sprint_m", 2000)).requires(Req.stat(Stat.AGI, 13))
                .cooldown(20, 1.5).energy(10).effect(SkillEffects::sprint).build());
        reg(b("appraisal", AWAKENING, PASSIVE, Items.SPYGLASS).ranks(3).level(10).rankStep(15).requires(Req.stat(Stat.PER, 20)).requires(Req.distinctKills(15)).build());
        reg(b("bloodlust", AWAKENING, DEFENSE, Items.CRIMSON_FUNGUS).level(15).requires(Req.counter("strong_kills", 25))
                .cooldown(30, 2).energy(25).effect(SkillEffects::bloodlust).build());
        reg(b("cross_slash", AWAKENING, ATTACK, Items.DIAMOND_SWORD).level(5).requires(Req.mastery(WeaponType.SWORD, 10))
                .cooldown(7, 0.4).energy(14).effect(SkillEffects::crossSlash).build());
        reg(b("earthshatter", AWAKENING, ATTACK, Items.NETHERITE_SWORD).level(5).requires(Req.mastery(WeaponType.GREATSWORD, 10))
                .cooldown(12, 0.8).energy(20).effect(SkillEffects::earthshatter).build());
        reg(b("vital_strike", AWAKENING, ATTACK, Items.SHEARS).level(5).requires(Req.mastery(WeaponType.DAGGER, 10))
                .cooldown(10, 0.6).energy(10).effect(SkillEffects::vitalStrike).build());
        reg(b("sunder", AWAKENING, ATTACK, Items.DIAMOND_AXE).level(5).requires(Req.mastery(WeaponType.AXE, 10))
                .cooldown(10, 0.6).energy(14).effect(SkillEffects::sunder).build());
        reg(b("piercing_thrust", AWAKENING, ATTACK, Items.TRIDENT).level(5).requires(Req.mastery(WeaponType.SPEAR, 10))
                .cooldown(8, 0.5).energy(14).effect(SkillEffects::piercingThrust).build());
        reg(b("ground_pound", AWAKENING, ATTACK, Items.MACE).level(5).requires(Req.mastery(WeaponType.MACE, 10))
                .cooldown(10, 0.6).energy(16).effect(SkillEffects::groundPound).build());
        reg(b("eagle_eye", AWAKENING, PASSIVE, Items.BOW).level(5).requires(Req.mastery(WeaponType.BOW, 10)).build());
        reg(b("heavy_bolts", AWAKENING, PASSIVE, Items.CROSSBOW).level(5).requires(Req.mastery(WeaponType.CROSSBOW, 10)).build());
        reg(b("perfect_guard", AWAKENING, DEFENSE, Items.SHIELD).level(5).requires(Req.mastery(WeaponType.SHIELD, 10)).requires(Req.counter("blocks", 50)).build());
        reg(b("mana_surge", AWAKENING, PASSIVE, Items.BLAZE_ROD).level(5).requires(Req.mastery(WeaponType.STAFF, 10)).build());
        reg(b("iron_fist", AWAKENING, PASSIVE, Items.IRON_INGOT).level(5).requires(Req.mastery(WeaponType.FIST, 10)).build());
        reg(b("counter_edge", AWAKENING, PASSIVE, Items.FLINT).level(8).requires(Req.counter("counters", 30)).build());
        reg(b("afterimage", AWAKENING, PASSIVE, Items.PHANTOM_MEMBRANE).level(12).requires(Req.counter("dodges", 40)).requires(Req.stat(Stat.AGI, 25)).build());
        reg(b("weak_point", AWAKENING, PASSIVE, Items.ARROW).level(12).requires(Req.counter("crits", 200)).requires(Req.stat(Stat.PER, 20)).build());

        // ============================== HIDDEN ==============================
        reg(b("tenacity", HIDDEN, PASSIVE, Items.CHAINMAIL_CHESTPLATE).level(10).requires(Req.counter("low_hp_kills", 10)).build());
        reg(b("second_wind", HIDDEN, PASSIVE, Items.TOTEM_OF_UNDYING).ranks(3).level(20).rankStep(15).requires(Req.achievement("undying")).build());
        reg(b("ruler_grip", HIDDEN, MAGIC, Items.DRAGON_BREATH).ranks(3).level(50).rankStep(15).requires(Req.bosses(2)).requires(Req.stat(Stat.INT, 30))
                .cooldown(30, 3).energy(40).effect(SkillEffects::rulerGrip).build());
    }

    private static void spec(Specialization s, String activeId, Item activeIcon, SkillEffect effect, double cd, double cdPerRank, float energy,
                             String passiveId, Item passiveIcon) {
        int column = s == s.parent.specializations()[0] ? 0 : 2;
        reg(b(activeId, SPEC, ATTACK, activeIcon).forSpec(s).level(10).cooldown(cd, cdPerRank).energy(energy).tree(column, 0).effect(effect).build());
        reg(b(passiveId, SPEC, PASSIVE, passiveIcon).forSpec(s).level(15).tree(column, 1).build());
    }

    private static void ult(HunterClass c, String id, Item icon, SkillEffect effect) {
        reg(b(id, SkillSource.ULTIMATE, SkillCategory.ULTIMATE, icon).forClass(c).ranks(3).level(40).rankStep(15).requires(Req.advanced())
                .cooldown(90, 10).energy(60).tree(3, 1).effect(effect).build());
    }
}
