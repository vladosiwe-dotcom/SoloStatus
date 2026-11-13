package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.combat.Threat;
import com.vladosiwe.solostatus.data.PlayerData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import static com.vladosiwe.solostatus.skill.SkillFx.*;

/**
 * General techniques: any class buys them with skill points and picks what suits its style.
 * Ideas borrowed from familiar anime and light-novel tropes (a parry window, sky-stepping, body
 * reinforcement, battle focus, thought acceleration, an overwhelming presence), tuned to be tools rather than
 * win buttons: short windows, real cooldowns, bosses resist the crowd control.
 *
 * Passive general skills are read where their stat lives: breathing_technique (StatFormulas.energyRegen),
 * iron_will (StatFormulas.debuffResist), acrobatics (StatFormulas.safeFall), weapon_adept (MasterySystem),
 * danger_sense and parry (CombatEvents.handleDefense), focus (CombatEvents.handleOffense).
 */
public final class GeneralSkills {
    private GeneralSkills() {}

    /** Parry: a short window in which the next melee blow is turned aside and the attacker is staggered. */
    public static boolean parry(SkillContext c) {
        ServerPlayer p = c.player();
        state(c.data(), c.level(), "parry", parryWindow(c.rank()));
        swing(p);
        sound(c.level(), p.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.6f);
        return true;
    }

    /** 0.4 s at rank 1, 0.8 s at rank 3. */
    public static int parryWindow(int rank) {
        return 6 + 2 * Math.max(1, rank) + (rank >= 3 ? 2 : 0);
    }

    /** Sky Step: kick off the air itself — a leap from the ground or a second jump in mid-air. */
    public static boolean airStep(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 look = horizontalLook(p);
        double up = c.scale(0.62, 0.06);
        double forward = c.scale(0.35, 0.08);
        p.setDeltaMovement(look.x * forward + p.getDeltaMovement().x * 0.5, up, look.z * forward + p.getDeltaMovement().z * 0.5);
        p.hurtMarked = true;
        p.resetFallDistance();
        ServerLevel level = c.level();
        ring(level, ParticleTypes.CLOUD, p.position(), 0.7, 10);
        sound(level, p.position(), SoundEvents.PHANTOM_FLAP, 0.7f, 1.6f);
        return true;
    }

    /** Body Reinforcement: channel the resource into muscles and skin for a few seconds. */
    public static boolean bodyReinforcement(SkillContext c) {
        ServerPlayer p = c.player();
        int ticks = 20 * (6 + 2 * c.rank());
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0, false, true));
        if (c.rank() >= 3) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0, false, true));
        particles(c.level(), ParticleTypes.ENCHANTED_HIT, p.position().add(0, 1, 0), 25, 0.4, 0.1);
        sound(c.level(), p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1f, 0.8f);
        return true;
    }

    /** Battle Focus: for a few seconds blows find weak points more often. */
    public static boolean focus(SkillContext c) {
        ServerPlayer p = c.player();
        state(c.data(), c.level(), "focus", 20 * (5 + c.rank()));
        particles(c.level(), SYSTEM_DUST, p.getEyePosition(), 12, 0.25, 0.01);
        sound(c.level(), p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        return true;
    }

    /** Extra crit chance while Battle Focus is active. */
    public static double focusCrit(PlayerData d, long now) {
        return d.stateActive("focus", now) ? 0.10 + 0.05 * (d.skillRank("focus") - 1) : 0;
    }

    /** Thought Acceleration: the world slows down around the hunter for a moment. */
    public static boolean thoughtAcceleration(SkillContext c) {
        ServerPlayer p = c.player();
        int ticks = 20 * (2 + c.rank());
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, ticks, 1, false, true));
        for (LivingEntity e : enemiesAround(p, p.position(), 8)) {
            debuff(p, e, MobEffects.MOVEMENT_SLOWDOWN, Threat.isBoss(e) ? ticks / 3 : ticks, Threat.isBoss(e) ? 0 : 1);
        }
        ring(c.level(), ParticleTypes.REVERSE_PORTAL, p.position().add(0, 0.2, 0), 3, 24);
        sound(c.level(), p.position(), SoundEvents.BEACON_POWER_SELECT, 0.7f, 2f);
        return true;
    }

    /**
     * Overwhelming Presence: enemies weaker than the hunter falter and lose their nerve; strong ones are only
     * slowed for a moment, bosses ignore it.
     */
    public static boolean overwhelmingPresence(SkillContext c) {
        ServerPlayer p = c.player();
        double radius = 6 + c.rank();
        double weakBelow = (1 + c.data().level * 0.06) * 0.6; // the same "expected threat" as kill XP
        boolean any = false;
        for (LivingEntity e : enemiesAround(p, p.position(), radius)) {
            if (Threat.isBoss(e)) continue;
            any = true;
            if (Threat.of(e) < weakBelow) {
                debuff(p, e, MobEffects.WEAKNESS, 80 + 20 * c.rank(), 0);
                debuff(p, e, MobEffects.MOVEMENT_SLOWDOWN, 80 + 20 * c.rank(), 1);
                if (e instanceof Mob m && m.getTarget() == p) m.setTarget(null);
            } else {
                debuff(p, e, MobEffects.MOVEMENT_SLOWDOWN, 40, 0);
            }
        }
        if (!any) return false;
        ring(c.level(), SHADOW_DUST, p.position().add(0, 0.3, 0), radius * 0.7, 40);
        sound(c.level(), p.position(), SoundEvents.WARDEN_ROAR, 0.35f, 1.5f);
        return true;
    }
}
