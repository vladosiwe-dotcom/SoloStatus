package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.combat.Threat;
import com.vladosiwe.solostatus.compat.ironsspells.IronsSpellsBridge;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.vladosiwe.solostatus.skill.SkillFx.*;

/**
 * The legendary skills, one per class, awakened by the class's legendary weapon and cast only while holding it
 * (SkillSystem.cast). Server side, like the other skills; numbers grow with the rank through c.scale.
 */
public final class LegendarySkills {
    private LegendarySkills() {}

    private static boolean alive(ServerPlayer p) {
        return p.isAlive() && !p.isRemoved();
    }

    /** Warrior, Mountain Cleaver: a leap and a slam that cracks the ground around the landing. */
    public static boolean mountainFall(SkillContext c) {
        ServerPlayer p = c.player();
        dash(p, horizontalLook(p), 1.1, 0.9);
        iframes(c, 24);
        double dmg = melee(p) * c.scale(3.0, 0.5);
        particles(c.level(), ParticleTypes.CLOUD, p.position(), 15, 0.5, 0.05);
        sound(c.level(), p.position(), SoundEvents.RAVAGER_ROAR, 0.8f, 1.2f);
        Scheduler.later(14, () -> {
            if (!alive(p)) return;
            Vec3 at = p.position();
            p.setDeltaMovement(p.getDeltaMovement().x * 0.2, -1.6, p.getDeltaMovement().z * 0.2); // brought down hard
            p.hurtMarked = true;
            Scheduler.later(4, () -> {
                if (!alive(p)) return;
                p.fallDistance = 0;
                Vec3 land = p.position();
                for (LivingEntity t : enemiesAround(p, land, 5)) {
                    hitPhysical(p, t, dmg);
                    launch(t, 0.7);
                    if (!Threat.isBoss(t)) stun(p, t, 20);
                }
                ServerLevel level = p.serverLevel();
                particles(level, ParticleTypes.EXPLOSION_EMITTER, land, 1, 0, 0);
                ring(level, ParticleTypes.CLOUD, land.add(0, 0.1, 0), 5, 60);
                ring(level, SYSTEM_DUST, land.add(0, 0.2, 0), 3, 40);
                sound(level, land, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.4f, 0.6f);
                sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.8f);
            });
        });
        return true;
    }

    /** Thief, Dusk Fang: blinks from enemy to enemy, striking each one from behind, then fades away. */
    public static boolean thousandShadows(SkillContext c) {
        ServerPlayer p = c.player();
        List<LivingEntity> targets = new ArrayList<>(enemiesAround(p, p.position(), 10));
        if (targets.isEmpty()) return false;
        targets.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        int count = Math.min(targets.size(), 4 + c.rank());
        double dmg = melee(p) * c.scale(1.4, 0.2);
        iframes(c, 6 * count + 10);
        Scheduler.repeat(count, 6, i -> {
            if (!alive(p)) return;
            LivingEntity t = targets.get(i);
            if (!t.isAlive()) return;
            Vec3 behind = t.position().subtract(t.getLookAngle().multiply(1, 0, 1).normalize().scale(1.1));
            if (!p.level().noCollision(p, p.getBoundingBox().move(behind.subtract(p.position())))) behind = t.position();
            particles(p.serverLevel(), SHADOW_DUST, p.position().add(0, 1, 0), 15, 0.3, 0.02);
            p.teleportTo(behind.x, behind.y, behind.z);
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
            swing(p);
            hitPhysical(p, t, dmg);
            particles(p.serverLevel(), SHADOW_DUST, t.getBoundingBox().getCenter(), 25, 0.4, 0.05);
            sound(p.serverLevel(), t.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.4f + i * 0.1f);
        });
        Scheduler.later(6 * count + 2, () -> {
            if (alive(p)) buff(p, p, MobEffects.INVISIBILITY, 40, 0);
        });
        return true;
    }

    /** Mage, Starwell Staff: a column of starlight that draws enemies in and gives mana back for each one it hurts. */
    public static boolean starWell(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 at = lookPoint(p, 24);
        double dmg = StatFormulas.baseSpellDamage(c.data()) * c.scale(0.8, 0.15);
        sound(c.level(), at, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.6f);
        Scheduler.repeat(8, 10, i -> {
            if (!alive(p)) return;
            ServerLevel level = p.serverLevel();
            int hit = 0;
            for (LivingEntity t : enemiesAround(p, at, 5)) {
                pull(t, at, 0.35);
                hitMagic(p, t, dmg);
                hit++;
            }
            if (hit > 0) IronsSpellsBridge.spendMana(p, -2f * hit);
            for (int k = 0; k < 8; k++) particles(level, ParticleTypes.END_ROD, at.add(0, k * 1.2, 0), 3, 0.3, 0.02);
            ring(level, ParticleTypes.ENCHANT, at.add(0, 0.2, 0), 5, 40);
            if (i == 7) { // the well bursts at the end
                for (LivingEntity t : enemiesAround(p, at, 5)) {
                    hitMagic(p, t, dmg * 2);
                    knockback(t, at, 1.2);
                }
                particles(level, ParticleTypes.FLASH, at.add(0, 1, 0), 1, 0, 0);
                sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.6f);
            }
        });
        return true;
    }

    /**
     * Defender, Last Bastion: a dome around the hunter for 6 s (+1 s per rank). Allies inside get Resistance II,
     * enemies are thrown out, hostile projectiles are stopped at its edge.
     */
    public static boolean unbreakableLine(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 at = p.position();
        double r = 6;
        int pulses = (int) c.scale(12, 2); // every half second
        sound(c.level(), at, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.8f);
        Scheduler.repeat(pulses, 10, i -> {
            if (!alive(p)) return;
            ServerLevel level = p.serverLevel();
            for (LivingEntity a : alliesAround(p, at, r)) buff(p, a, MobEffects.DAMAGE_RESISTANCE, 15, 1);
            for (LivingEntity e : enemiesAround(p, at, r)) {
                knockback(e, at, 0.9);
                if (i % 2 == 0) hitPhysical(p, e, melee(p) * 0.3);
            }
            for (Projectile pr : level.getEntitiesOfClass(Projectile.class, new AABB(at, at).inflate(r))) {
                if (pr.getOwner() instanceof LivingEntity owner && !isAlly(p, owner) && owner != p) {
                    particles(level, ParticleTypes.ENCHANTED_HIT, pr.position(), 6, 0.1, 0.1);
                    pr.discard();
                }
            }
            ring(level, HOLY_DUST, at.add(0, 0.2, 0), r, 60);
            ring(level, HOLY_DUST, at.add(0, 2.2, 0), r * 0.8, 40);
            ring(level, HOLY_DUST, at.add(0, 3.6, 0), r * 0.45, 24);
        });
        return true;
    }

    /** Archer, Skyhunt Bow: marks a place; a second later the sky answers with a rain of arrows and a beam of light. */
    public static boolean heavensVerdict(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 at = lookPoint(p, 32);
        int n = (int) c.scale(20, 5);
        double arrowDmg = 6.0 * c.scale(1.0, 0.15);
        double beam = 12.0 * c.scale(1.0, 0.2) * (1 + StatFormulas.projectileDamage(c.data()));
        ring(c.level(), ParticleTypes.END_ROD, at.add(0, 0.2, 0), 4, 40);
        sound(c.level(), p.position(), SoundEvents.ARROW_SHOOT, 1f, 0.5f);
        Scheduler.later(20, () -> {
            if (!alive(p)) return;
            ServerLevel level = p.serverLevel();
            for (LivingEntity t : enemiesAround(p, at, 2.5)) hitPhysical(p, t, beam);
            for (int k = 0; k < 16; k++) particles(level, ParticleTypes.END_ROD, at.add(0, k, 0), 4, 0.15, 0.01);
            sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.6f);
            Scheduler.repeat(n, 2, i -> {
                if (!alive(p)) return;
                double a = p.getRandom().nextDouble() * Math.PI * 2;
                double d = p.getRandom().nextDouble() * 4;
                skyArrow(p, at.add(Math.cos(a) * d, 0, Math.sin(a) * d), arrowDmg);
            });
        });
        return true;
    }

    /**
     * Support, Dawn Scepter: daybreak around the hunter. Allies within 12 blocks are healed, cleansed and shielded;
     * the undead burn in the light.
     */
    public static boolean daybreak(SkillContext c) {
        ServerPlayer p = c.player();
        double heal = c.scale(8, 4) * (1 + StatFormulas.magicPower(c.data()));
        double holy = StatFormulas.baseSpellDamage(c.data()) * c.scale(2.0, 0.3);
        for (LivingEntity a : alliesAround(p, p.position(), 12)) {
            heal(p, a, heal);
            for (var e : new ArrayList<>(a.getActiveEffects())) {
                if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) a.removeEffect(e.getEffect());
            }
            buff(p, a, MobEffects.ABSORPTION, 200, 1);
        }
        Set<LivingEntity> burned = new HashSet<>();
        for (LivingEntity e : enemiesAround(p, p.position(), 12)) {
            if (e.getType().is(EntityTypeTags.UNDEAD) && burned.add(e)) {
                hitMagic(p, e, holy);
                e.igniteForSeconds(4);
            }
        }
        particles(c.level(), ParticleTypes.FLASH, p.position().add(0, 2, 0), 1, 0, 0);
        particles(c.level(), HOLY_DUST, p.position().add(0, 2, 0), 120, 5, 0.05);
        ring(c.level(), ParticleTypes.END_ROD, p.position().add(0, 0.2, 0), 12, 90);
        sound(c.level(), p.position(), SoundEvents.BELL_RESONATE, 1.2f, 1.4f);
        sound(c.level(), p.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.8f);
        return true;
    }
}
