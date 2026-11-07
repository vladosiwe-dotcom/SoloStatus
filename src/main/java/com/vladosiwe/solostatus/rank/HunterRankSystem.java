package com.vladosiwe.solostatus.rank;

import com.vladosiwe.solostatus.achievement.Achievements;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.mastery.MasteryMath;
import com.vladosiwe.solostatus.network.Notices;
import com.vladosiwe.solostatus.skill.SkillFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

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

    public static int score(PlayerData d) {
        int score = d.level * 2;
        int masterySum = 0;
        for (double xp : d.mastery.values()) masterySum += MasteryMath.level(xp);
        score += Math.min(150, masterySum);
        score += (int) Math.min(100, d.counter("strong_kills") / 5);
        int bosses = 0;
        for (String b : d.bossesKilled) bosses += BOSS_SCORE.getOrDefault(b, 15);
        bosses += (int) Math.min(40, d.counter("boss_kills") * 2);
        score += Math.min(200, bosses);
        int ach = 0;
        for (String id : d.achievements) {
            var def = Achievements.get(id);
            ach += def != null && def.hidden() ? 8 : 5;
        }
        score += Math.min(150, ach);
        if (d.advanced) score += 30;
        return score;
    }

    public static HunterRank compute(PlayerData d, int score) {
        HunterRank result = HunterRank.E;
        for (HunterRank r : HunterRank.VALUES) {
            if (score >= r.threshold && gate(d, r)) result = r;
        }
        return result;
    }

    private static boolean gate(PlayerData d, HunterRank r) {
        return switch (r) {
            case A -> d.bossesKilled.size() >= 1;
            case S -> d.bossesKilled.size() >= 2 && d.level >= 60 && d.advanced;
            case NATIONAL -> d.bossesKilled.size() >= 3 && d.level >= 90
                    && d.bossesKilled.contains("minecraft:ender_dragon") && d.bossesKilled.contains("minecraft:wither");
            default -> true;
        };
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
            d.markDirty();
            player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 60, 0.5, 1, 0.5, 0.4);
            player.serverLevel().sendParticles(SkillFx.SYSTEM_DUST, player.getX(), player.getY() + 1, player.getZ(), 80, 1, 1.5, 1, 0.02);
            SkillFx.sound(player.serverLevel(), player.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
            Notices.send(player, Notices.RANK_UP, Component.translatable("solostatus.notice.rank_up"), rank.displayName(),
                    net.minecraft.world.item.Items.NETHER_STAR);
        } else if (rank.ordinal() < d.rank.ordinal()) {
            // Ранг не понижается (например, после сброса) — достигнутое признание сохраняется.
        }
    }
}
