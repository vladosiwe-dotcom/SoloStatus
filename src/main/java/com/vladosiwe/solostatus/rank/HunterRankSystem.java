package com.vladosiwe.solostatus.rank;

import com.vladosiwe.solostatus.achievement.Achievements;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.mastery.MasteryMath;
import com.vladosiwe.solostatus.network.Notices;
import com.vladosiwe.solostatus.skill.SkillFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Hunter Rank — общая сила охотника, не только уровень.
 *
 * Очки силы (каждая составляющая ограничена, поэтому одной прокачкой высокий ранг не получить):
 *   уровень × 2 (≤200) + сумма уровней мастерства (≤150) + сильные противники / 5 (≤100)
 *   + боссы (≤200) + достижения (≤150) + Смена пути (30).
 * Ворота: A — хотя бы один босс; S — 2 разных босса, 60+ уровень и Смена пути;
 * National — Дракон Края и Иссушитель повержены, 3+ разных боссов, 90+ уровень.
 */
public final class HunterRankSystem {
    private static final Map<String, Integer> BOSS_SCORE = Map.of(
            "minecraft:ender_dragon", 40,
            "minecraft:wither", 30,
            "minecraft:warden", 30,
            "minecraft:elder_guardian", 10);

    private HunterRankSystem() {}

    /** The parts of the rank score, as the rank window shows them. */
    public record Breakdown(int level, int mastery, int strong, int bosses, int achievements, int path) {
        public int total() {
            return level + mastery + strong + bosses + achievements + path;
        }
    }

    /** One condition of a rank: a translation key with its arguments, and whether it is met. */
    public record Gate(String key, Object[] args, boolean met) {}

    public static Breakdown breakdown(PlayerData d) {
        int masterySum = 0;
        for (double xp : d.mastery.values()) masterySum += MasteryMath.level(xp);
        int bosses = 0;
        for (String b : d.bossesKilled) bosses += BOSS_SCORE.getOrDefault(b, 15);
        bosses += (int) Math.min(40, d.counter("boss_kills") * 2);
        int ach = 0;
        for (String id : d.achievements) {
            var def = Achievements.get(id);
            if (def == null) continue;
            // slayer titles are a grind of one kind of mob: they count for less
            ach += def.category() == com.vladosiwe.solostatus.achievement.AchievementCategory.HUNT ? 2 : def.hidden() ? 8 : 5;
        }
        return new Breakdown(Math.min(200, d.level * 2), Math.min(150, masterySum), (int) Math.min(100, d.counter("strong_kills") / 5),
                Math.min(200, bosses), Math.min(150, ach), d.advanced ? 30 : 0);
    }

    public static int score(PlayerData d) {
        return breakdown(d).total();
    }

    public static HunterRank compute(PlayerData d, int score) {
        HunterRank result = HunterRank.E;
        for (HunterRank r : HunterRank.VALUES) {
            if (score >= r.threshold && gate(d, r)) result = r;
        }
        return result;
    }

    /** What a rank asks besides its score: bosses, level and the Path Change trial for the high ranks. */
    public static java.util.List<Gate> gates(PlayerData d, HunterRank r) {
        java.util.List<Gate> out = new java.util.ArrayList<>();
        int bosses = d.bossesKilled.size();
        switch (r) {
            case A -> out.add(new Gate("solostatus.rank.gate.bosses", new Object[]{Math.min(bosses, 1), 1}, bosses >= 1));
            case S -> {
                out.add(new Gate("solostatus.rank.gate.bosses", new Object[]{Math.min(bosses, 2), 2}, bosses >= 2));
                out.add(new Gate("solostatus.rank.gate.level", new Object[]{60}, d.level >= 60));
                out.add(new Gate("solostatus.rank.gate.advanced", new Object[0], d.advanced));
            }
            case NATIONAL -> {
                out.add(new Gate("solostatus.rank.gate.bosses", new Object[]{Math.min(bosses, 3), 3}, bosses >= 3));
                out.add(new Gate("solostatus.rank.gate.level", new Object[]{90}, d.level >= 90));
                out.add(new Gate("solostatus.rank.gate.advanced", new Object[0], d.advanced));
                out.add(new Gate("solostatus.rank.gate.dragon", new Object[0], d.bossesKilled.contains("minecraft:ender_dragon")));
                out.add(new Gate("solostatus.rank.gate.wither", new Object[0], d.bossesKilled.contains("minecraft:wither")));
            }
            default -> { }
        }
        return out;
    }

    private static boolean gate(PlayerData d, HunterRank r) {
        for (Gate g : gates(d, r)) if (!g.met()) return false;
        return true;
    }

    public static void update(ServerPlayer player, PlayerData d) {
        int score = score(d);
        HunterRank rank = compute(d, score);
        if (score != d.rankScore) {
            d.rankScore = score;
            d.dirty = true;
        }
        if (rank.ordinal() > d.rank.ordinal()) {
            d.rank = rank;
            com.vladosiwe.solostatus.data.History.add(player, d, "rank", rank.letter, 0);
            d.markDirty();
            com.vladosiwe.solostatus.level.LevelSystem.grantXp(player, 400L * rank.ordinal() * rank.ordinal(), com.vladosiwe.solostatus.level.XpReason.RANK);
            player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 60, 0.5, 1, 0.5, 0.4);
            player.serverLevel().sendParticles(SkillFx.SYSTEM_DUST, player.getX(), player.getY() + 1, player.getZ(), 80, 1, 1.5, 1, 0.02);
            Notices.send(player, Notices.RANK_UP, Component.translatable("solostatus.notice.rank_up"), rank.displayName(),
                    net.minecraft.world.item.Items.NETHER_STAR);
        } else if (rank.ordinal() < d.rank.ordinal()) {
            // Ранг не понижается (например, после сброса) — достигнутое признание сохраняется.
        }
    }
}
