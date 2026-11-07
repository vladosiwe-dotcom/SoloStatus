package com.vladosiwe.solostatus.achievement;

import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.level.LevelSystem;
import com.vladosiwe.solostatus.network.Notices;
import com.vladosiwe.solostatus.registry.ModItems;
import com.vladosiwe.solostatus.skill.Requirement;
import com.vladosiwe.solostatus.skill.SkillFx;
import com.vladosiwe.solostatus.stats.AttributeSystem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Проверка и выдача достижений. Награда выдаётся строго один раз: id добавляется в набор достижений
 * до выдачи награды, а сам набор сохраняется вместе с профилем.
 */
public final class AchievementSystem {
    private AchievementSystem() {}

    public static void check(ServerPlayer player, PlayerData d) {
        for (AchievementDef def : Achievements.all()) {
            if (d.achievements.contains(def.id())) continue;
            boolean met = true;
            for (Requirement r : def.requirements()) {
                if (!r.met(d)) { met = false; break; }
            }
            if (met) grant(player, d, def);
        }
    }

    public static void grant(ServerPlayer player, PlayerData d, AchievementDef def) {
        if (!d.achievements.add(def.id())) return;
        d.unseenAchievements.add(def.id());
        d.skillPoints += def.sp();
        if (def.orbs() > 0) {
            ItemStack orbs = new ItemStack(ModItems.RESET_ORB.get(), def.orbs());
            if (!player.getInventory().add(orbs)) player.drop(orbs, false);
        }
        if (def.perk()) AttributeSystem.apply(player);
        d.markDirty();

        var level = player.serverLevel();
        level.sendParticles(def.hidden() ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.5, player.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
        SkillFx.sound(level, player.position(), def.hidden() ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);

        Component body = def.perk() ? Component.translatable("solostatus.notice.achievement.perk", def.description(), def.perkText()) : def.description();
        Notices.send(player, def.hidden() ? Notices.HIDDEN_ACHIEVEMENT : Notices.ACHIEVEMENT, def.name(), body);
        if (def.xp() > 0) LevelSystem.grantXp(player, def.xp());
    }
}
