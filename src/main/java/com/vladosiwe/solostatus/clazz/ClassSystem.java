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
        Notices.send(player, Notices.CLASS, Component.translatable("solostatus.notice.class_chosen"), hc.displayName(), com.vladosiwe.solostatus.registry.SystemIcon.of(hc));
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
        Notices.send(player, Notices.CLASS, Component.translatable("solostatus.notice.spec_chosen"), spec.displayName(), com.vladosiwe.solostatus.registry.SystemIcon.of(spec.parent));
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
        com.vladosiwe.solostatus.level.LevelSystem.grantXp(player, 8000, com.vladosiwe.solostatus.level.XpReason.TRIAL);
        d.achievements.add("class_trial");
        d.unseenAchievements.add("class_trial");
        d.markDirty();
        Notices.send(player, Notices.CLASS, Component.translatable("solostatus.notice.advanced"), d.specialization.advancedName(),
                net.minecraft.world.item.Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        SkillDef ult = Skills.ultimate(d.hunterClass);
        if (ult != null) SkillUnlockSystem.awaken(player, d, ult);
    }

    /** Mage skills replaced by the Iron's Spells school branches; those learned before are refunded. */
    private static final java.util.Map<String, Boolean> RETIRED = java.util.Map.of(
            "mana_bolt", true, "frost_nova", false, "flame_burst", false, "chain_lightning", false, "mana_shield", false);

    /**
     * Brings an older profile up to date at login: skills that no longer exist are taken away with their skill points
     * given back (a starting skill was free at rank 1), and starting skills the class got since are granted.
     */
    public static void migrate(ServerPlayer player) {
        PlayerData d = Hunter.get(player);
        if (!d.hasClass()) return;
        boolean changed = false;
        for (var e : RETIRED.entrySet()) {
            Integer rank = d.skills.remove(e.getKey());
            if (rank == null) continue;
            int refund = 0;
            for (int r = 1; r <= rank; r++) refund += r == 1 ? (e.getValue() ? 0 : 1) : r <= 3 ? 1 : 2;
            d.skillPoints += refund;
            for (int i = 0; i < d.slots.length; i++) if (e.getKey().equals(d.slots[i])) d.slots[i] = null;
            d.cooldowns.remove(e.getKey());
            changed = true;
        }
        for (SkillDef def : Skills.forClass(d.hunterClass)) {
            if (def.source == SkillSource.CLASS_START && !d.hasSkill(def.id)) {
                SkillSystem.grant(player, d, def);
                changed = true;
            }
        }
        if (changed) {
            d.markDirty();
            Notices.send(player, Notices.INFO, Component.translatable("solostatus.notice.skills_updated"), Component.translatable("solostatus.notice.skills_updated.desc"));
        }
    }
}
