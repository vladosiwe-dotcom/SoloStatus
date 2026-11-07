package com.vladosiwe.solostatus.clazz;

import com.vladosiwe.solostatus.config.SoloConfig;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.network.Notices;
import com.vladosiwe.solostatus.skill.*;
import com.vladosiwe.solostatus.stats.AttributeSystem;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * Выбор класса (первое пробуждение системы), специализации и Испытание смены пути.
 */
public final class ClassSystem {
    private ClassSystem() {}

    public static void chooseClass(ServerPlayer player, HunterClass hc) {
        PlayerData d = Hunter.get(player);
        if (d.hasClass() || hc == null) return; // смена класса не предусмотрена — защита от повторного выбора
        d.hunterClass = hc;
        d.stats[hc.primary.ordinal()] += 3;
        d.stats[hc.secondary.ordinal()] += 2;
        d.skillPoints += 1; // стартовое очко навыков
        for (SkillDef def : Skills.forClass(hc)) {
            if (def.source == SkillSource.CLASS_START) SkillSystem.grant(player, d, def);
        }
        d.unseenSkills.clear(); // стартовые навыки не помечаем «новыми»
        AttributeSystem.apply(player);
        d.energy = (float) StatFormulas.maxEnergy(d);
        d.markDirty();

        player.serverLevel().sendParticles(SkillFx.SYSTEM_DUST, player.getX(), player.getY() + 1, player.getZ(), 150, 1, 1.5, 1, 0.05);
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 60, 0.4, 2, 0.4, 0.15);
        SkillFx.sound(player.serverLevel(), player.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1f);
        SkillFx.sound(player.serverLevel(), player.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 0.9f);
        Notices.send(player, Notices.CLASS, Component.translatable("solostatus.notice.class_chosen"), hc.displayName(), hc.icon());
    }

    public static void chooseSpec(ServerPlayer player, Specialization spec) {
        PlayerData d = Hunter.get(player);
        if (spec == null || d.specialization != null || d.hunterClass != spec.parent) return;
        if (d.level < SoloConfig.SPECIALIZATION_LEVEL.get()) {
            Notices.warn(player, "solostatus.error.level");
            return;
        }
        d.specialization = spec;
        AttributeSystem.apply(player);
        d.markDirty();
        player.serverLevel().sendParticles(SkillFx.SYSTEM_DUST, player.getX(), player.getY() + 1, player.getZ(), 100, 0.8, 1.2, 0.8, 0.05);
        SkillFx.sound(player.serverLevel(), player.position(), SoundEvents.BEACON_POWER_SELECT, 1f, 1f);
        Notices.send(player, Notices.CLASS, Component.translatable("solostatus.notice.spec_chosen"), spec.displayName(), spec.parent.icon());
    }

    /** Условия Испытания смены пути для класса (кроме уровня и специализации). */
    public static List<Requirement> trialRequirements(HunterClass hc) {
        return switch (hc) {
            case WARRIOR -> List.of(Req.counter("strong_kills", 100), Req.counter("melee_kills", 1000), Req.bosses(1));
            case THIEF -> List.of(Req.counter("backstabs", 300), Req.counter("dodges", 150), Req.bosses(1));
            case MAGE -> List.of(Req.counter("magic_hits", 1500), Req.counter("strong_kills", 50), Req.bosses(1));
            case DEFENDER -> List.of(Req.counter("blocks", 400), Req.counter("perfect_guards", 100), Req.bosses(1));
            case ARCHER -> List.of(Req.counter("projectile_kills", 800), Req.counter("long_shots", 150), Req.bosses(1));
            case SUPPORT -> List.of(Req.counter("healed", 3000), Req.counter("buffs_given", 300), Req.bosses(1));
        };
    }

    public static boolean trialAvailable(PlayerData d) {
        return d.specialization != null && !d.advanced && d.level >= SoloConfig.ADVANCEMENT_LEVEL.get();
    }

    public static void checkTrial(ServerPlayer player, PlayerData d) {
        if (!trialAvailable(d) || d.hunterClass == null) return;
        for (Requirement r : trialRequirements(d.hunterClass)) if (!r.met(d)) return;
        d.advanced = true;
        d.achievements.add("class_trial");
        d.unseenAchievements.add("class_trial");
        d.markDirty();
        Notices.send(player, Notices.CLASS, Component.translatable("solostatus.notice.advanced"), d.specialization.advancedName(),
                net.minecraft.world.item.Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        SkillDef ult = Skills.ultimate(d.hunterClass);
        if (ult != null) SkillUnlockSystem.awaken(player, d, ult);
    }
}
