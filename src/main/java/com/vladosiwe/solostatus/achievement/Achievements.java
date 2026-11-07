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
 * Список достижений. Очки навыков выдаются только за значимые вехи (суммарно 9 за все видимые),
 * чтобы достижения дополняли, но не заменяли прогрессию.
 */
public final class Achievements {
    private static final Map<String, AchievementDef> BY_ID = new LinkedHashMap<>();

    private Achievements() {}

    private static void reg(String id, boolean hidden, Item icon, int xp, int sp, int orbs, boolean perk, Requirement... reqs) {
        BY_ID.put(id, new AchievementDef(id, hidden, icon, List.of(reqs), xp, sp, orbs, perk));
    }

    @Nullable
    public static AchievementDef get(String id) {
        return BY_ID.get(id);
    }

    public static Collection<AchievementDef> all() {
        return BY_ID.values();
    }

    static {
        // ------------------------------ Видимые ------------------------------
        reg("first_blood", false, Items.WOODEN_SWORD, 50, 0, 0, false, Req.counter("kills", 1));
        reg("hunter_100", false, Items.IRON_SWORD, 500, 0, 0, false, Req.counter("kills", 100));
        reg("hunter_1000", false, Items.DIAMOND_SWORD, 5000, 1, 0, false, Req.counter("kills", 1000));
        reg("strong_25", false, Items.IRON_CHESTPLATE, 1500, 0, 0, false, Req.counter("strong_kills", 25));
        reg("strong_250", false, Items.DIAMOND_CHESTPLATE, 8000, 1, 0, false, Req.counter("strong_kills", 250));
        reg("first_boss", false, Items.DRAGON_HEAD, 3000, 1, 0, false, Req.bosses(1));
        reg("boss_hunter", false, Items.NETHER_STAR, 10000, 1, 1, false, Req.bosses(3));
        reg("guard_50", false, Items.SHIELD, 1000, 0, 0, false, Req.counter("perfect_guards", 50));
        reg("dodge_100", false, Items.FEATHER, 1000, 0, 0, false, Req.counter("dodges", 100));
        reg("crit_500", false, Items.ARROW, 1000, 0, 0, false, Req.counter("crits", 500));
        reg("combo_100", false, Items.BLAZE_POWDER, 1500, 0, 0, false, Req.counter("combos", 100));
        reg("magic_500", false, Items.ENCHANTED_BOOK, 1500, 0, 0, false, Req.counter("magic_hits", 500));
        reg("healer_1000", false, Items.GLISTERING_MELON_SLICE, 1500, 0, 0, false, Req.counter("healed", 1000));
        reg("mastery_25", false, Items.IRON_INGOT, 2000, 1, 0, false, anyMastery(25));
        reg("mastery_50", false, Items.NETHERITE_INGOT, 6000, 1, 0, false, anyMastery(50));
        reg("multi_master", false, Items.CRAFTING_TABLE, 4000, 1, 0, false, masteries(3, 20));
        reg("level_10", false, Items.EXPERIENCE_BOTTLE, 0, 0, 0, false, Req.level(10));
        reg("level_50", false, Items.EXPERIENCE_BOTTLE, 0, 1, 0, false, Req.level(50));
        reg("level_100", false, Items.BEACON, 0, 1, 1, false, Req.level(100));
        reg("rank_c", false, Items.COPPER_INGOT, 2000, 0, 0, false, rank(HunterRank.C));
        reg("rank_a", false, Items.GOLD_INGOT, 6000, 0, 0, false, rank(HunterRank.A));
        reg("rank_s", false, Items.DIAMOND, 12000, 0, 1, false, rank(HunterRank.S));
        reg("combo_1000", false, Items.BLAZE_ROD, 4000, 0, 0, true, Req.counter("combos", 1000));
        reg("skill_master", false, Items.EXPERIENCE_BOTTLE, 4000, 0, 0, true, Req.counter("skills_used", 1000));
        reg("iron_wall", false, Items.SHIELD, 4000, 0, 0, true, Req.counter("blocks", 1000));
        reg("sharp_eye", false, Items.SPYGLASS, 4000, 0, 0, true, Req.counter("long_shots", 500));
        reg("magic_scholar", false, Items.BOOKSHELF, 4000, 0, 0, true, Req.counter("magic_hits", 3000));
        reg("collector", false, Items.CHEST, 3000, 0, 0, true, Req.distinctKills(40));
        reg("awakened_10", false, Items.END_CRYSTAL, 5000, 1, 0, false, Req.awakenedCount(10));
        reg("class_trial", false, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 0, 0, 0, false, Req.advanced());

        // ------------------------------ Скрытые ------------------------------
        reg("bone_lord", true, Items.BONE, 2000, 0, 0, true, Req.counter("skeleton_kills", 100));
        reg("arachnophobe", true, Items.SPIDER_EYE, 2000, 0, 0, true, Req.counter("spider_kills", 100));
        reg("night_hunter", true, Items.CLOCK, 2000, 0, 0, true, Req.counter("night_kills", 200));
        reg("undying", true, Items.TOTEM_OF_UNDYING, 3000, 0, 0, true, Req.counter("undying", 1));
        reg("untouchable", true, Items.PHANTOM_MEMBRANE, 3000, 0, 0, true, Req.counter("flawless_strong", 10));
        reg("shadow_of_death", true, Items.WITHER_ROSE, 3000, 0, 0, true, Req.counter("backstab_kills", 50));
        reg("marathon", true, Items.LEATHER_BOOTS, 2000, 0, 0, true, Req.counter("sprint_m", 50000));
        reg("dragon_slayer", true, Items.DRAGON_EGG, 5000, 0, 0, true, boss("minecraft:ender_dragon"));
        reg("witherbane", true, Items.WITHER_SKELETON_SKULL, 5000, 0, 0, true, boss("minecraft:wither"));
        reg("giant_killer", true, Items.IRON_GOLEM_SPAWN_EGG, 4000, 0, 0, true, Req.counter("giant_kills", 25));
        reg("frenzy", true, Items.FIRE_CHARGE, 3000, 0, 0, true, Req.counter("rampages", 25));
        reg("deep_dark", true, Items.SCULK_CATALYST, 5000, 0, 0, true, boss("minecraft:warden"));
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
