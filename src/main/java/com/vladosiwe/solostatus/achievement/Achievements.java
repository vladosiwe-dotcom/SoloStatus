package com.vladosiwe.solostatus.achievement;

import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.mastery.MasteryMath;
import com.vladosiwe.solostatus.rank.HunterRank;
import com.vladosiwe.solostatus.skill.Req;
import com.vladosiwe.solostatus.skill.Requirement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Достижения охотника.
 *
 * Принципы:
 *  - обычные достижения дают опыт, иногда очки навыков (только значимые вехи) или Сферы перерождения;
 *  - постоянных незаметных «+%» больше нет: сильные эффекты дают ТИТУЛЫ, и активен только один;
 *  - титулы — только за по-настоящему трудные достижения;
 *  - несколько скрытых достижений открывают редкие скрытые навыки.
 */
public final class Achievements {
    private static final Map<String, AchievementDef> BY_ID = new LinkedHashMap<>();

    private Achievements() {}

    private static void reg(String id, boolean hidden, Item icon, int xp, int sp, int orbs, Requirement... reqs) {
        BY_ID.put(id, new AchievementDef(id, hidden, icon, List.of(reqs), xp, sp, orbs, false, null));
    }

    /** Достижение, открывающее титул. */
    private static void title(String id, boolean hidden, Item icon, int xp, Requirement... reqs) {
        BY_ID.put(id, new AchievementDef(id, hidden, icon, List.of(reqs), xp, 0, 0, true, null));
    }

    /** Достижение, открывающее редкий навык. */
    private static void skill(String id, boolean hidden, Item icon, int xp, String skillId, Requirement... reqs) {
        BY_ID.put(id, new AchievementDef(id, hidden, icon, List.of(reqs), xp, 0, 0, false, skillId));
    }

    @Nullable
    public static AchievementDef get(String id) {
        return BY_ID.get(id);
    }

    public static Collection<AchievementDef> all() {
        return BY_ID.values();
    }

    public static List<AchievementDef> titles() {
        return BY_ID.values().stream().filter(AchievementDef::title).toList();
    }

    static {
        // ---------------- Путь охотника ----------------
        reg("first_blood", false, Items.WOODEN_SWORD, 50, 0, 0, Req.counter("kills", 1));
        reg("first_awakening", false, Items.ENDER_EYE, 300, 0, 0, Req.awakenedCount(1));
        reg("path_chosen", false, Items.COMPASS, 500, 0, 0, specChosen());
        reg("level_10", false, Items.EXPERIENCE_BOTTLE, 0, 0, 0, Req.level(10));
        reg("level_50", false, Items.EXPERIENCE_BOTTLE, 0, 1, 0, Req.level(50));
        reg("level_100", false, Items.BEACON, 0, 1, 1, Req.level(100));
        reg("class_trial", false, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 0, 0, 0, Req.advanced());
        reg("awakened_10", false, Items.END_CRYSTAL, 5000, 1, 0, Req.awakenedCount(10));
        reg("rebirth", false, Items.RECOVERY_COMPASS, 200, 0, 0, Req.counter("respecs", 1));

        // ---------------- Бой ----------------
        reg("hunter_100", false, Items.IRON_SWORD, 500, 0, 0, Req.counter("kills", 100));
        reg("hunter_1000", false, Items.DIAMOND_SWORD, 5000, 1, 0, Req.counter("kills", 1000));
        title("hunter_5000", false, Items.NETHERITE_SWORD, 15000, Req.counter("kills", 5000));
        reg("strong_25", false, Items.IRON_CHESTPLATE, 1500, 0, 0, Req.counter("strong_kills", 25));
        reg("strong_250", false, Items.DIAMOND_CHESTPLATE, 8000, 1, 0, Req.counter("strong_kills", 250));
        reg("guard_50", false, Items.SHIELD, 1000, 0, 0, Req.counter("perfect_guards", 50));
        reg("parry_master", false, Items.SHIELD, 6000, 1, 0, Req.counter("perfect_guards", 500));
        reg("dodge_100", false, Items.FEATHER, 1000, 0, 0, Req.counter("dodges", 100));
        reg("crit_500", false, Items.ARROW, 1000, 0, 0, Req.counter("crits", 500));
        reg("combo_100", false, Items.BLAZE_POWDER, 1500, 0, 0, Req.counter("combos", 100));
        reg("magic_500", false, Items.ENCHANTED_BOOK, 1500, 0, 0, Req.counter("magic_hits", 500));
        reg("healer_1000", false, Items.GLISTERING_MELON_SLICE, 1500, 0, 0, Req.counter("healed", 1000));

        // ---------------- Боссы и ранги ----------------
        reg("first_boss", false, Items.DRAGON_HEAD, 3000, 1, 0, Req.bosses(1));
        reg("boss_hunter", false, Items.NETHER_STAR, 10000, 1, 1, Req.bosses(3));
        title("dragon_slayer", false, Items.DRAGON_EGG, 5000, boss("minecraft:ender_dragon"));
        title("witherbane", false, Items.WITHER_SKELETON_SKULL, 5000, boss("minecraft:wither"));
        title("deep_dark", false, Items.SCULK_CATALYST, 5000, boss("minecraft:warden"));
        reg("rank_c", false, Items.COPPER_INGOT, 2000, 0, 0, rank(HunterRank.C));
        reg("rank_a", false, Items.GOLD_INGOT, 6000, 0, 0, rank(HunterRank.A));
        reg("rank_s", false, Items.DIAMOND, 12000, 0, 1, rank(HunterRank.S));
        title("rank_national", false, Items.BEACON, 25000, rank(HunterRank.NATIONAL));

        // ---------------- Мастерство и снаряжение ----------------
        reg("mastery_25", false, Items.IRON_INGOT, 2000, 1, 0, anyMastery(25));
        title("mastery_50", false, Items.NETHERITE_INGOT, 6000, anyMastery(50));
        reg("multi_master", false, Items.CRAFTING_TABLE, 4000, 1, 0, masteries(3, 20));
        reg("treasure", false, Items.GOLD_BLOCK, 1500, 0, 1, Req.counter("legendary_found", 1));

        // ---------------- Скрытые ----------------
        title("bone_lord", true, Items.BONE, 3000, Req.counter("skeleton_kills", 500));
        title("night_hunter", true, Items.CLOCK, 3000, Req.counter("night_kills", 1000));
        title("undying", true, Items.TOTEM_OF_UNDYING, 3000, Req.counter("undying", 1));
        title("untouchable", true, Items.PHANTOM_MEMBRANE, 4000, Req.counter("flawless_strong", 25));
        title("shadow_of_death", true, Items.WITHER_ROSE, 4000, Req.counter("backstab_kills", 300));
        title("giant_killer", true, Items.IRON_GOLEM_SPAWN_EGG, 4000, Req.counter("giant_kills", 50));
        title("frenzy", true, Items.FIRE_CHARGE, 3000, Req.counter("rampages", 50));
        reg("solo_raid", true, Items.TOTEM_OF_UNDYING, 4000, 0, 1, Req.counter("solo_boss", 1));
        reg("marathon", true, Items.LEATHER_BOOTS, 2000, 0, 0, Req.counter("sprint_m", 50000));
        skill("legion", true, Items.BONE_BLOCK, 8000, "shadow_legion", Req.bosses(3), Req.counter("kills", 2000));
        skill("sovereign", true, Items.DRAGON_BREATH, 10000, "ruler_grip", boss("minecraft:ender_dragon"), boss("minecraft:wither"));
    }

    private static Requirement specChosen() {
        return new Requirement() {
            public double progress(PlayerData d) { return d.specialization != null ? 1 : 0; }
            public Component describe(PlayerData d) { return Component.translatable("solostatus.req.spec"); }
        };
    }

    private static Requirement anyMastery(int lvl) {
        return new Requirement() {
            public double progress(PlayerData d) {
                int best = 0;
                for (double xp : d.mastery.values()) best = Math.max(best, MasteryMath.level(xp));
                return Math.min(1, best / (double) lvl);
            }
            public Component describe(PlayerData d) { return Component.translatable("solostatus.req.any_mastery", lvl); }
        };
    }

    private static Requirement masteries(int count, int lvl) {
        return new Requirement() {
            public double progress(PlayerData d) {
                int n = 0;
                for (double xp : d.mastery.values()) if (MasteryMath.level(xp) >= lvl) n++;
                return Math.min(1, n / (double) count);
            }
            public Component describe(PlayerData d) { return Component.translatable("solostatus.req.masteries", count, lvl); }
        };
    }

    private static Requirement rank(HunterRank r) {
        return new Requirement() {
            public double progress(PlayerData d) { return d.rank.ordinal() >= r.ordinal() ? 1 : 0; }
            public Component describe(PlayerData d) { return Component.translatable("solostatus.req.rank", r.displayName()); }
        };
    }

    private static Requirement boss(String id) {
        return new Requirement() {
            public double progress(PlayerData d) { return d.bossesKilled.contains(id) ? 1 : 0; }
            public Component describe(PlayerData d) { return Component.translatable("solostatus.req.boss"); }
        };
    }
}
