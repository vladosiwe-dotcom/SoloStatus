package com.vladosiwe.solostatus.level;

import com.vladosiwe.solostatus.config.SoloConfig;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.network.Notices;
import com.vladosiwe.solostatus.stats.AttributeSystem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Уровни 1..100, опыт и очки развития.
 *
 * Очки характеристик: statPointsPerLevel (3) за каждый уровень — отдельная система.
 * Очки навыков: 1 за каждый чётный уровень + 1 бонусное каждые 10 уровней (итого 60 к 100 уровню)
 * + 1 стартовое + награды достижений + ограниченные «пробуждения» после 100 уровня.
 * Обоснование — в docs/DESIGN.md (раздел «Очки навыков»).
 */
public final class LevelSystem {
    /** Опыт после 100 уровня, нужный для одного «пробуждения предела» (растёт на 25% за каждое). */
    public static final long OVERFLOW_BASE = 150_000;
    public static final int MAX_OVERFLOW_AWAKENINGS = 20;

    private LevelSystem() {}

    public static long xpToNext(int level) {
        return Math.round(40 * Math.pow(level, 1.45) + 60);
    }

    public static long overflowNeed(int awakenings) {
        return Math.round(OVERFLOW_BASE * (1 + awakenings * 0.25));
    }

    public static int skillPointsForLevel(int level) {
        int sp = level % 2 == 0 ? 1 : 0;
        if (level % 10 == 0) sp++;
        return sp;
    }

    public static void grantXp(ServerPlayer player, double rawAmount) {
        if (rawAmount <= 0) return;
        PlayerData d = Hunter.get(player);
        if (!d.hasClass()) return; // система ещё не пробуждена
        long amount = Math.max(1, Math.round(rawAmount * SoloConfig.XP_MULTIPLIER.get()));

        if (d.level >= PlayerData.MAX_LEVEL) {
            addOverflow(player, d, amount);
            d.markDirty();
            return;
        }
        d.xp += amount;
        int levelsGained = 0;
        while (d.level < PlayerData.MAX_LEVEL && d.xp >= xpToNext(d.level)) {
            d.xp -= xpToNext(d.level);
            d.level++;
            levelsGained++;
            d.freeStatPoints += SoloConfig.STAT_POINTS_PER_LEVEL.get();
            d.skillPoints += skillPointsForLevel(d.level);
        }
        if (d.level >= PlayerData.MAX_LEVEL && d.xp > 0) {
            long rest = d.xp;
            d.xp = 0;
            addOverflow(player, d, rest);
        }
        if (levelsGained > 0) onLevelUp(player, d, levelsGained);
        d.markDirty();
    }

    private static void addOverflow(ServerPlayer player, PlayerData d, long amount) {
        if (d.overflowAwakenings >= MAX_OVERFLOW_AWAKENINGS) return;
        d.overflowXp += amount;
        while (d.overflowAwakenings < MAX_OVERFLOW_AWAKENINGS && d.overflowXp >= overflowNeed(d.overflowAwakenings)) {
            d.overflowXp -= overflowNeed(d.overflowAwakenings);
            d.overflowAwakenings++;
            d.skillPoints++;
            Notices.send(player, Notices.LEVEL_UP,
                    Component.translatable("solostatus.notice.limit_break"),
                    Component.translatable("solostatus.notice.limit_break.desc", d.overflowAwakenings, MAX_OVERFLOW_AWAKENINGS));
        }
        if (d.overflowAwakenings >= MAX_OVERFLOW_AWAKENINGS) d.overflowXp = 0;
    }

    private static void onLevelUp(ServerPlayer player, PlayerData d, int gained) {
        AttributeSystem.apply(player);
        // Как в первоисточнике: повышение уровня полностью восстанавливает охотника.
        player.setHealth(player.getMaxHealth());
        player.getFoodData().eat(20, 1.0f);
        player.clearFire();
        d.energy = (float) com.vladosiwe.solostatus.stats.StatFormulas.maxEnergy(d);

        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 60, 0.6, 1.0, 0.6, 0.08);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.4f);

        Notices.send(player, Notices.LEVEL_UP,
                Component.translatable("solostatus.notice.level_up", d.level),
                Component.translatable("solostatus.notice.level_up.desc", gained * SoloConfig.STAT_POINTS_PER_LEVEL.get()));
    }

    /** Административная установка уровня (команда). Очки пересчитываются по формулам. */
    public static void setLevel(ServerPlayer player, int level) {
        PlayerData d = Hunter.get(player);
        int target = Math.max(1, Math.min(PlayerData.MAX_LEVEL, level));
        while (d.level < target) {
            d.level++;
            d.freeStatPoints += SoloConfig.STAT_POINTS_PER_LEVEL.get();
            d.skillPoints += skillPointsForLevel(d.level);
        }
        if (target < d.level) d.level = target; // понижение не отнимает очки — используйте /solostatus reset
        d.xp = 0;
        AttributeSystem.apply(player);
        d.markDirty();
    }
}
