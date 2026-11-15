package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.clazz.Specialization;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.vladosiwe.solostatus.skill.SkillFx.*;

/**
 * Реализации активных навыков. Каждый метод — самостоятельная механика:
 * поиск целей, урон, контроль, перемещение, визуальные эффекты и звук. Анимация — взмах оружием
 * (при наличии Epic Fight анимации подключаются через compat.epicfight, см. docs/INTEGRATIONS.md).
 */
public final class SkillEffects {
    private SkillEffects() {}

    private static double spell(SkillContext c) {
        return StatFormulas.baseSpellDamage(c.data());
    }

    private static boolean alive(ServerPlayer p) {
        return p.isAlive() && !p.isRemoved();
    }

    // =============================== WARRIOR ===============================

    public static boolean powerStrike(SkillContext c) {
        ServerPlayer p = c.player();
        LivingEntity t = closestInCone(p, 3.5, 70);
        if (t == null) return false;
        swing(p);
        hitPhysical(p, t, melee(p) * c.scale(1.6, 0.2));
        knockback(t, p.position(), 0.9);
        particles(c.level(), ParticleTypes.CRIT, t.getBoundingBox().getCenter(), 15, 0.3, 0.3);
        particles(c.level(), ParticleTypes.SWEEP_ATTACK, t.getBoundingBox().getCenter(), 1, 0, 0);
        sound(c.level(), t.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
        return true;
    }

    public static boolean whirlwind(SkillContext c) {
        ServerPlayer p = c.player();
        swing(p);
        int mod = SkillMods.of(c.data(), "whirlwind"); // 1 Vortex: draws in, harder; 2 Blood Whirl: heals per enemy hit
        double dmg = melee(p) * c.scale(1.0, 0.15) * (mod == 1 ? 1.15 : 1.0);
        int hit = 0;
        for (LivingEntity t : enemiesAround(p, p.position(), 3.5)) {
            hitPhysical(p, t, dmg);
            if (mod == 1) pull(t, p.position(), 0.5);
            else knockback(t, p.position(), 0.5);
            hit++;
        }
        if (mod == 2 && hit > 0) p.heal(p.getMaxHealth() * 0.02f * Math.min(5, hit));
        ring(c.level(), ParticleTypes.SWEEP_ATTACK, p.position().add(0, 1, 0), 2.5, 10);
        sound(c.level(), p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
        return true;
    }

    public static boolean charge(SkillContext c) {
        ServerPlayer p = c.player();
        dash(p, horizontalLook(p), c.scale(1.6, 0.2), 0.1);
        iframes(c, 6);
        sound(c.level(), p.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
        double dmg = melee(p) * c.scale(1.2, 0.15);
        Scheduler.later(6, () -> {
            if (!alive(p)) return;
            swing(p);
            for (LivingEntity t : enemiesAround(p, p.position(), 2.5)) {
                hitPhysical(p, t, dmg);
                debuff(p, t, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
            }
            particles(p.serverLevel(), ParticleTypes.CLOUD, p.position(), 12, 0.6, 0.05);
        });
        return true;
    }

    public static boolean warCry(SkillContext c) {
        ServerPlayer p = c.player();
        int dur = (int) c.scale(160, 40);
        buff(p, p, MobEffects.DAMAGE_BOOST, dur, c.rank() >= 4 ? 1 : 0);
        buff(p, p, MobEffects.DAMAGE_RESISTANCE, 120, 0);
        for (LivingEntity t : enemiesAround(p, p.position(), 8)) debuff(p, t, MobEffects.WEAKNESS, 120, 0);
        ring(c.level(), BLOOD_DUST, p.position().add(0, 0.2, 0), 3, 24);
        sound(c.level(), p.position(), SoundEvents.RAVAGER_ROAR, 1f, 1.2f);
        return true;
    }

    public static boolean devastatingBlow(SkillContext c) {
        ServerPlayer p = c.player();
        LivingEntity t = closestInCone(p, 4, 70);
        if (t == null) return false;
        swing(p);
        int mod = SkillMods.of(c.data(), "devastating_blow"); // 1 Earthquake: hits around the target; 2 Execution: crushes the wounded
        double dmg = melee(p) * c.scale(3.0, 0.4);
        if (mod == 2 && t.getHealth() < t.getMaxHealth() * 0.3f) dmg *= 2;
        hitPhysical(p, t, dmg);
        if (mod != 2) stun(p, t, 30);
        if (mod == 1) {
            for (LivingEntity e : enemiesAround(p, t.position(), 2.5)) if (e != t) hitPhysical(p, e, dmg * 0.5);
            ring(c.level(), ParticleTypes.CLOUD, t.position().add(0, 0.1, 0), 2.5, 24);
        }
        particles(c.level(), ParticleTypes.EXPLOSION, t.position(), 2, 0.2, 0);
        sound(c.level(), t.position(), SoundEvents.ANVIL_LAND, 0.8f, 0.6f);
        return true;
    }

    public static boolean titanSlash(SkillContext c) {
        ServerPlayer p = c.player();
        List<LivingEntity> targets = enemiesInCone(p, 6, 120);
        swing(p);
        double dmg = melee(p) * c.scale(5.0, 1.0);
        boolean berserker = isSpec(c, Specialization.BERSERKER);
        for (LivingEntity t : targets) {
            hitPhysical(p, t, dmg);
            knockback(t, p.position(), 1.2);
            if (berserker) p.heal((float) (dmg * 0.2 / Math.max(1, targets.size())));
        }
        if (isSpec(c, Specialization.WARLORD)) {
            for (LivingEntity a : alliesAround(p, p.position(), 10)) buff(p, a, MobEffects.DAMAGE_BOOST, 200, 1);
        }
        Vec3 look = horizontalLook(p);
        for (int i = 1; i <= 6; i++) particles(c.level(), ParticleTypes.SWEEP_ATTACK, p.position().add(look.scale(i)).add(0, 1, 0), 2, 0.6, 0);
        sound(c.level(), p.position(), SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 1.5f);
        sound(c.level(), p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);
        return true;
    }

    // =============================== THIEF ===============================

    public static boolean quickDash(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 move = p.getDeltaMovement();
        Vec3 dir = new Vec3(move.x, 0, move.z).lengthSqr() > 0.002 ? new Vec3(move.x, 0, move.z) : horizontalLook(p);
        dash(p, dir, c.scale(1.4, 0.15), 0.05);
        iframes(c, 8);
        particles(c.level(), SHADOW_DUST, p.position().add(0, 1, 0), 15, 0.4, 0.02);
        sound(c.level(), p.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7f, 1.6f);
        return true;
    }

    public static boolean flurry(SkillContext c) {
        ServerPlayer p = c.player();
        LivingEntity t = closestInCone(p, 3.5, 70);
        if (t == null) return false;
        int mod = SkillMods.of(c.data(), "flurry"); // 1 Hailstorm: more, lighter blows; 2 Venomed Blades: every blow poisons
        int hits = 4 + c.rank() / 2 + (mod == 1 ? 2 : 0);
        double dmg = melee(p) * c.scale(0.45, 0.05) * (mod == 1 ? 0.8 : 1.0);
        Scheduler.repeat(3, hits, i -> {
            if (!alive(p) || !t.isAlive() || t.distanceToSqr(p) > 25) return;
            swing(p);
            hitPhysical(p, t, dmg);
            if (mod == 2) {
                debuff(p, t, MobEffects.POISON, 40, 0);
                if (i == hits - 1) debuff(p, t, MobEffects.WEAKNESS, 80, 0);
            }
            particles(p.serverLevel(), ParticleTypes.CRIT, t.getBoundingBox().getCenter(), 6, 0.3, 0.2);
            sound(p.serverLevel(), t.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.4f + i * 0.08f);
        });
        return true;
    }

    public static boolean vanish(SkillContext c) {
        ServerPlayer p = c.player();
        int dur = (int) c.scale(80, 20);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, dur, 0, false, false, true));
        for (Mob m : c.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), m -> m.getTarget() == p)) m.setTarget(null);
        state(c.data(), c.level(), "vanish_strike", dur);
        particles(c.level(), ParticleTypes.LARGE_SMOKE, p.position().add(0, 1, 0), 30, 0.4, 0.02);
        sound(c.level(), p.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.8f, 1.5f);
        return true;
    }

    /** Thief's Concealment: everyone loses the hunter; the first blow from it ends it with extra damage. */
    public static boolean concealment(SkillContext c) {
        ServerPlayer p = c.player();
        Concealment.start(p, (int) c.scale(120, 20));
        particles(c.level(), ParticleTypes.LARGE_SMOKE, p.position().add(0, 1, 0), 40, 0.5, 0.02);
        sound(c.level(), p.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.9f, 0.7f);
        return true;
    }

    public static boolean poisonBlade(SkillContext c) {
        state(c.data(), c.level(), "poison_blade", 200);
        particles(c.level(), ParticleTypes.ITEM_SLIME, c.player().position().add(0, 1, 0), 15, 0.4, 0.05);
        sound(c.level(), c.player().position(), SoundEvents.BREWING_STAND_BREW, 0.8f, 1.3f);
        return true;
    }

    public static boolean assassinate(SkillContext c) {
        ServerPlayer p = c.player();
        LivingEntity t = closestInCone(p, 3.5, 70);
        if (t == null) return false;
        double mult = c.scale(2.0, 0.3);
        if (t.getHealth() <= t.getMaxHealth() * 0.3f) mult *= 3;
        int mod = SkillMods.of(c.data(), "assassinate"); // 1 Silent Death: a kill hides and half-resets; 2 Open Wound: the target withers
        swing(p);
        hitPhysical(p, t, melee(p) * mult);
        if (mod == 1 && !t.isAlive()) {
            buff(p, p, MobEffects.INVISIBILITY, 60, 0);
            com.vladosiwe.solostatus.data.PlayerData d = c.data();
            Long ready = d.cooldowns.get("assassinate");
            long now = c.level().getGameTime();
            // the cooldown is set after the effect, so it is halved one tick later
            Scheduler.later(1, () -> {
                Long r = d.cooldowns.get("assassinate");
                if (r != null && r > now) d.cooldowns.put("assassinate", now + (r - now) / 2);
                d.markDirty();
            });
        }
        if (mod == 2 && t.isAlive()) debuff(p, t, MobEffects.WITHER, 80, 1);
        particles(c.level(), BLOOD_DUST, t.getBoundingBox().getCenter(), 20, 0.3, 0.1);
        sound(c.level(), t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.6f);
        return true;
    }

    public static boolean thousandCuts(SkillContext c) {
        ServerPlayer p = c.player();
        double dmg = melee(p) * c.scale(0.35, 0.1);
        iframes(c, 26);
        Scheduler.repeat(2, 12, i -> {
            if (!alive(p)) return;
            swing(p);
            for (LivingEntity t : enemiesAround(p, p.position(), 4)) {
                hitPhysical(p, t, dmg);
                particles(p.serverLevel(), ParticleTypes.SWEEP_ATTACK, t.getBoundingBox().getCenter(), 1, 0.3, 0);
            }
            sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.2f + i * 0.05f);
        });
        if (isSpec(c, Specialization.SHADOW)) p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
        return true;
    }

    // =============================== MAGE ===============================

    public static boolean arcaneCataclysm(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 at = lookPoint(p, 30);
        double dmg = spell(c) * c.scale(2.0, 0.5) * attunement(c);
        boolean elementalist = isSpec(c, Specialization.ELEMENTALIST);
        boolean arcanist = isSpec(c, Specialization.ARCANIST);
        Scheduler.repeat(10, 3, i -> {
            if (!alive(p)) return;
            ServerLevel level = p.serverLevel();
            for (LivingEntity t : enemiesAround(p, at, 6)) {
                hitMagic(p, t, dmg);
                if (elementalist) t.igniteForSeconds(3);
                if (arcanist) {
                    Vec3 pull = at.subtract(t.position()).normalize().scale(0.6);
                    t.setDeltaMovement(t.getDeltaMovement().add(pull));
                    t.hurtMarked = true;
                }
            }
            ring(level, SYSTEM_DUST, at.add(0, 0.2, 0), 6, 48);
            particles(level, ParticleTypes.EXPLOSION_EMITTER, at, 1, 0, 0);
            particles(level, elementalist ? ParticleTypes.FLAME : ParticleTypes.REVERSE_PORTAL, at, 80, 3, 0.1);
            sound(level, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, 1.2f, 0.7f + i * 0.15f);
        });
        return true;
    }

    /** Бонус Стихийника к стихийной магии. */
    private static double attunement(SkillContext c) {
        return 1 + c.data().skillRank("elemental_attunement") * 0.06;
    }

    // =============================== DEFENDER ===============================

    public static boolean shieldBash(SkillContext c) {
        ServerPlayer p = c.player();
        int mod = SkillMods.of(c.data(), "shield_bash"); // 1 Ram: reaches farther with a lunge; 2 Concussion: dazes everyone around, weaker
        LivingEntity t = closestInCone(p, mod == 1 ? 6 : 3.5, 70);
        if (t == null) return false;
        if (mod == 1 && t.distanceTo(p) > 2.5) dash(p, t.position().subtract(p.position()), 1.5, 0.05);
        boolean shield = p.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD) || p.getMainHandItem().is(net.minecraft.world.item.Items.SHIELD)
                || p.getOffhandItem().getItem() instanceof net.minecraft.world.item.ShieldItem;
        swing(p);
        double bash = melee(p) * c.scale(1.0, 0.15) * (shield ? 1.3 : 1.0) * (mod == 2 ? 0.7 : 1.0);
        hitPhysical(p, t, bash);
        knockback(t, p.position(), 1.4);
        stun(p, t, 30);
        if (mod == 2) {
            for (LivingEntity e : enemiesAround(p, t.position(), 2.5)) {
                if (e == t) continue;
                hitPhysical(p, e, bash);
                if (!com.vladosiwe.solostatus.combat.Threat.isBoss(e)) stun(p, e, 20);
            }
        }
        sound(c.level(), t.position(), SoundEvents.SHIELD_BLOCK, 1f, 0.7f);
        particles(c.level(), ParticleTypes.CRIT, t.getBoundingBox().getCenter(), 12, 0.3, 0.2);
        return true;
    }

    public static boolean taunt(SkillContext c) {
        ServerPlayer p = c.player();
        double r = c.scale(10, 2);
        int count = 0;
        for (Mob m : c.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r), m -> isAreaEnemy(p, m) || m instanceof net.minecraft.world.entity.monster.Enemy)) {
            if (p.isAlliedTo(m) || ownedBy(m, p)) continue;
            m.setTarget(p);
            count++;
            particles(c.level(), ParticleTypes.ANGRY_VILLAGER, m.position().add(0, m.getBbHeight() + 0.3, 0), 1, 0, 0);
        }
        buff(p, p, MobEffects.DAMAGE_RESISTANCE, 80, 0);
        sound(c.level(), p.position(), SoundEvents.IRON_GOLEM_ATTACK, 1f, 0.6f);
        return count > 0;
    }

    public static boolean defensiveStance(SkillContext c) {
        state(c.data(), c.level(), "defensive_stance", (int) c.scale(120, 20));
        c.player().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) c.scale(120, 20), 0, false, false));
        ring(c.level(), SYSTEM_DUST, c.player().position().add(0, 0.1, 0), 1.3, 20);
        sound(c.level(), c.player().position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1f, 0.8f);
        return true;
    }

    public static boolean counterAttack(SkillContext c) {
        state(c.data(), c.level(), "counter", 60);
        particles(c.level(), ParticleTypes.ENCHANTED_HIT, c.player().position().add(0, 1, 0), 20, 0.5, 0.1);
        sound(c.level(), c.player().position(), SoundEvents.SHIELD_BLOCK, 0.8f, 1.5f);
        return true;
    }

    public static boolean fortress(SkillContext c) {
        ServerPlayer p = c.player();
        int mod = SkillMods.of(c.data(), "fortress"); // 1 Living Wall: enemies turn on you; 2 Healing Keep: allies regenerate
        for (LivingEntity a : alliesAround(p, p.position(), 8)) {
            buff(p, a, MobEffects.DAMAGE_RESISTANCE, 160, c.rank() >= 4 ? 1 : 0);
            buff(p, a, MobEffects.ABSORPTION, 160, 0);
            if (mod == 2) buff(p, a, MobEffects.REGENERATION, 120, 0);
        }
        if (mod == 1) {
            for (LivingEntity e : enemiesAround(p, p.position(), 8)) if (e instanceof Mob m) m.setTarget(p);
        }
        ring(c.level(), HOLY_DUST, p.position().add(0, 0.2, 0), 8, 60);
        sound(c.level(), p.position(), SoundEvents.BEACON_ACTIVATE, 1f, 0.9f);
        return true;
    }

    public static boolean unbreakable(SkillContext c) {
        ServerPlayer p = c.player();
        int dur = (int) c.scale(120, 20);
        state(c.data(), c.level(), "unbreakable", dur);
        for (Mob m : c.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(12), m -> isAreaEnemy(p, m) || m instanceof net.minecraft.world.entity.monster.Enemy)) {
            if (!p.isAlliedTo(m) && !ownedBy(m, p)) m.setTarget(p);
        }
        if (isSpec(c, Specialization.GUARDIAN)) {
            for (LivingEntity a : alliesAround(p, p.position(), 10)) if (a != p) buff(p, a, MobEffects.DAMAGE_RESISTANCE, dur, 1);
        }
        if (isSpec(c, Specialization.JUGGERNAUT)) buff(p, p, MobEffects.DAMAGE_BOOST, dur, 1);
        ring(c.level(), HOLY_DUST, p.position().add(0, 1, 0), 1.5, 30);
        sound(c.level(), p.position(), SoundEvents.ANVIL_LAND, 1f, 0.5f);
        return true;
    }

    // =============================== ARCHER ===============================

    public static boolean powerShot(SkillContext c) {
        ServerPlayer p = c.player();
        int mod = SkillMods.of(c.data(), "power_shot"); // 1 Stunning Shot; 2 Double Shot: two lighter arrows
        double dmg = 6.0 * c.scale(1.2, 0.15);
        if (mod == 2) {
            arrow(p, -2.5f, 0, 3.5f, dmg * 0.7, true);
            arrow(p, 2.5f, 0, 3.5f, dmg * 0.7, true);
        } else {
            var a = arrow(p, 0, 0, 3.5f, dmg, true);
            if (mod == 1) a.getPersistentData().putInt("solostatus_stun", 20);
        }
        sound(c.level(), p.position(), SoundEvents.ARROW_SHOOT, 1f, 0.6f);
        particles(c.level(), ParticleTypes.CRIT, p.getEyePosition().add(p.getLookAngle()), 10, 0.1, 0.1);
        return true;
    }

    public static boolean arrowVolley(SkillContext c) {
        ServerPlayer p = c.player();
        int n = 5 + c.rank();
        float spread = 40f;
        for (int i = 0; i < n; i++) {
            float yaw = -spread / 2 + spread * i / (n - 1);
            arrow(p, yaw, 0, 2.8f, 4.0, false);
        }
        sound(c.level(), p.position(), SoundEvents.ARROW_SHOOT, 1f, 1.2f);
        return true;
    }

    public static boolean knockbackShot(SkillContext c) {
        ServerPlayer p = c.player();
        var arrow = arrow(p, 0, 0, 3.0f, 5.0 * c.scale(1.0, 0.1), false);
        arrow.getPersistentData().putDouble("solostatus_knock", c.scale(1.5, 0.3));
        sound(c.level(), p.position(), SoundEvents.CROSSBOW_SHOOT, 1f, 0.7f);
        return true;
    }

    public static boolean rainOfArrows(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 at = lookPoint(p, 30);
        int n = (int) c.scale(10, 2);
        int mod = SkillMods.of(c.data(), "rain_of_arrows"); // 1 Fire Rain: burning arrows; 2 Focused Rain: a tighter, harder rain
        double dmg = 4.0 * c.scale(1.0, 0.1) * (mod == 2 ? 1.4 : 1.0);
        double spread = mod == 2 ? 1.5 : 3;
        Scheduler.repeat(2, n, i -> {
            if (!alive(p)) return;
            double a = p.getRandom().nextDouble() * Math.PI * 2;
            double r = p.getRandom().nextDouble() * spread;
            var arrow = skyArrow(p, at.add(Math.cos(a) * r, 0, Math.sin(a) * r), dmg);
            if (mod == 1 && arrow != null) arrow.igniteForSeconds(100);
        });
        ring(c.level(), ParticleTypes.CRIT, at.add(0, 0.2, 0), 3, 30);
        sound(c.level(), p.position(), SoundEvents.ARROW_SHOOT, 1f, 0.5f);
        return true;
    }

    public static boolean huntersMark(SkillContext c) {
        ServerPlayer p = c.player();
        LivingEntity t = raycast(p, 40, false);
        if (t == null) return false;
        mark(t, "mark", p, 200);
        debuff(p, t, MobEffects.GLOWING, 200, 0);
        line(c.level(), ParticleTypes.END_ROD, p.getEyePosition(), t.getBoundingBox().getCenter(), 0.7);
        sound(c.level(), t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.6f);
        return true;
    }

    public static boolean stormOfArrows(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 at = lookPoint(p, 30);
        double radius = isSpec(c, Specialization.RANGER) ? 7 : 5;
        double dmg = (isSpec(c, Specialization.SHARPSHOOTER) ? 7.0 : 5.0) * c.scale(1.0, 0.25);
        buff(p, p, MobEffects.MOVEMENT_SPEED, 80, 1);
        Scheduler.repeat(2, 30, i -> {
            if (!alive(p)) return;
            for (int k = 0; k < 2; k++) {
                double a = p.getRandom().nextDouble() * Math.PI * 2;
                double r = p.getRandom().nextDouble() * radius;
                skyArrow(p, at.add(Math.cos(a) * r, 0, Math.sin(a) * r), dmg);
            }
        });
        ring(c.level(), ParticleTypes.END_ROD, at.add(0, 0.2, 0), radius, 50);
        sound(c.level(), p.position(), SoundEvents.ENDER_DRAGON_FLAP, 1f, 1.5f);
        return true;
    }

    // =============================== SUPPORT ===============================

    public static boolean mend(SkillContext c) {
        ServerPlayer p = c.player();
        LivingEntity t = raycast(p, 16, true);
        if (t == null) t = p;
        int mod = SkillMods.of(c.data(), "mend"); // 1 Chain Mend: one more ally; 2 Renewal: part now, the rest over time
        double amount = c.scale(4.0, 1.5) * (1 + StatFormulas.magicPower(c.data()));
        heal(p, t, mod == 2 ? amount * 0.7 : amount);
        if (mod == 2) buff(p, t, MobEffects.REGENERATION, 80, 1);
        if (mod == 1) {
            LivingEntity target = t;
            for (LivingEntity a : alliesAround(p, t.position(), 6)) {
                if (a == target || a.getHealth() >= a.getMaxHealth()) continue;
                heal(p, a, amount * 0.5);
                line(c.level(), ParticleTypes.HAPPY_VILLAGER, target.getBoundingBox().getCenter(), a.getBoundingBox().getCenter(), 0.5);
                break;
            }
        }
        particles(c.level(), ParticleTypes.HAPPY_VILLAGER, t.getBoundingBox().getCenter(), 12, 0.4, 0.05);
        sound(c.level(), t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.5f);
        return true;
    }

    public static boolean blessing(SkillContext c) {
        ServerPlayer p = c.player();
        int dur = (int) c.scale(200, 40);
        for (LivingEntity a : alliesAround(p, p.position(), 8)) {
            buff(p, a, MobEffects.DAMAGE_BOOST, dur, 0);
            buff(p, a, MobEffects.MOVEMENT_SPEED, dur, 0);
            particles(c.level(), HOLY_DUST, a.getBoundingBox().getCenter(), 10, 0.4, 0);
        }
        sound(c.level(), p.position(), SoundEvents.BEACON_POWER_SELECT, 1f, 1.3f);
        return true;
    }

    public static boolean barrier(SkillContext c) {
        ServerPlayer p = c.player();
        for (LivingEntity a : alliesAround(p, p.position(), 8)) {
            buff(p, a, MobEffects.ABSORPTION, 300, Math.max(0, c.rank() / 2));
            ring(c.level(), SYSTEM_DUST, a.position().add(0, 1, 0), 0.8, 16);
        }
        sound(c.level(), p.position(), SoundEvents.BEACON_ACTIVATE, 0.8f, 1.5f);
        return true;
    }

    public static boolean purify(SkillContext c) {
        ServerPlayer p = c.player();
        for (LivingEntity a : alliesAround(p, p.position(), 8)) {
            List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> bad = new ArrayList<>();
            for (MobEffectInstance inst : a.getActiveEffects()) {
                if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) bad.add(inst.getEffect());
            }
            bad.forEach(a::removeEffect);
            heal(p, a, 2 * c.rank());
            particles(c.level(), ParticleTypes.END_ROD, a.getBoundingBox().getCenter(), 10, 0.4, 0.05);
        }
        sound(c.level(), p.position(), SoundEvents.ZOMBIE_VILLAGER_CURE, 0.6f, 1.6f);
        return true;
    }

    public static boolean sanctuary(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 at = p.position();
        double heal = c.scale(1.0, 0.5);
        int mod = SkillMods.of(c.data(), "sanctuary"); // 1 Hallowed Ground: burns enemies inside; 2 Refuge: allies inside are shielded
        double holy = spell(c) * 0.3;
        Scheduler.repeat(20, 8, i -> {
            if (!alive(p)) return;
            ServerLevel level = p.serverLevel();
            for (LivingEntity a : alliesAround(p, at, 6)) {
                heal(p, a, heal);
                if (mod == 2) buff(p, a, MobEffects.DAMAGE_RESISTANCE, 30, 0);
            }
            for (LivingEntity e : enemiesAround(p, at, 6)) {
                debuff(p, e, MobEffects.MOVEMENT_SLOWDOWN, 30, 0);
                if (mod == 1) hitMagic(p, e, holy);
            }
            ring(level, HOLY_DUST, at.add(0, 0.1, 0), 6, 50);
        });
        sound(c.level(), at, SoundEvents.BELL_RESONATE, 1f, 1.2f);
        return true;
    }

    public static boolean divineIntervention(SkillContext c) {
        ServerPlayer p = c.player();
        int dur = (int) c.scale(120, 20);
        for (LivingEntity a : alliesAround(p, p.position(), 12)) {
            if (a instanceof ServerPlayer ally) {
                state(com.vladosiwe.solostatus.data.Hunter.get(ally), c.level(), "guardian_angel", dur);
            }
            heal(p, a, a.getMaxHealth() * 0.3);
            if (isSpec(c, Specialization.CLERIC)) {
                a.getActiveEffects().stream().filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                        .map(MobEffectInstance::getEffect).toList().forEach(a::removeEffect);
            }
            particles(c.level(), ParticleTypes.TOTEM_OF_UNDYING, a.getBoundingBox().getCenter(), 20, 0.5, 0.3);
        }
        if (isSpec(c, Specialization.ENCHANTER)) {
            for (LivingEntity e : enemiesAround(p, p.position(), 12)) {
                debuff(p, e, MobEffects.WEAKNESS, dur, 1);
                debuff(p, e, MobEffects.MOVEMENT_SLOWDOWN, dur, 1);
            }
        }
        sound(c.level(), p.position(), SoundEvents.TOTEM_USE, 0.8f, 1.2f);
        return true;
    }

    // =============================== SPECIALIZATIONS ===============================

    public static boolean berserk(SkillContext c) {
        state(c.data(), c.level(), "berserk", (int) c.scale(200, 20));
        particles(c.level(), BLOOD_DUST, c.player().position().add(0, 1, 0), 40, 0.5, 0.1);
        sound(c.level(), c.player().position(), SoundEvents.RAVAGER_ROAR, 1f, 0.8f);
        return true;
    }

    public static boolean earthbreaker(SkillContext c) {
        ServerPlayer p = c.player();
        swing(p);
        double dmg = melee(p) * c.scale(1.5, 0.25);
        for (LivingEntity t : enemiesAround(p, p.position(), 4)) {
            hitPhysical(p, t, dmg);
            launch(t, 0.6);
        }
        for (LivingEntity a : alliesAround(p, p.position(), 8)) buff(p, a, MobEffects.DAMAGE_RESISTANCE, 100, 0);
        int aftershock = c.data().skillRank("aftershock"); // the ground shakes once more a second later
        if (aftershock > 0) {
            Vec3 at = p.position();
            double second = dmg * (0.2 + 0.2 * aftershock);
            Scheduler.later(20, () -> {
                if (!alive(p)) return;
                for (LivingEntity t : enemiesAround(p, at, 4.5)) hitPhysical(p, t, second);
                ring(p.serverLevel(), ParticleTypes.CLOUD, at.add(0, 0.1, 0), 4.5, 40);
                sound(p.serverLevel(), at, SoundEvents.MACE_SMASH_GROUND, 1f, 0.6f);
            });
        }
        particles(c.level(), ParticleTypes.EXPLOSION, p.position(), 3, 1.5, 0);
        ring(c.level(), ParticleTypes.CLOUD, p.position().add(0, 0.1, 0), 4, 40);
        sound(c.level(), p.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1f, 0.8f);
        return true;
    }

    public static boolean shadowStep(SkillContext c) {
        ServerPlayer p = c.player();
        LivingEntity t = raycast(p, 16, false);
        if (t == null) return false;
        Vec3 behind = t.position().subtract(t.getLookAngle().multiply(1, 0, 1).normalize().scale(1.2));
        if (!c.level().noCollision(p, p.getBoundingBox().move(behind.subtract(p.position())))) behind = t.position();
        particles(c.level(), SHADOW_DUST, p.position().add(0, 1, 0), 20, 0.4, 0.02);
        p.teleportTo(behind.x, behind.y, behind.z);
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
        swing(p);
        hitPhysical(p, t, melee(p) * c.scale(1.4, 0.2) * 1.5);
        int echo = c.data().skillRank("shadow_echo"); // a shadow strikes the same foe half a second later
        if (echo > 0) {
            double second = melee(p) * c.scale(1.4, 0.2) * (0.15 + 0.15 * echo);
            Scheduler.later(10, () -> {
                if (!alive(p) || !t.isAlive()) return;
                hitPhysical(p, t, second);
                particles(p.serverLevel(), SHADOW_DUST, t.getBoundingBox().getCenter(), 25, 0.4, 0.05);
                sound(p.serverLevel(), t.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 0.6f);
            });
        }
        particles(c.level(), SHADOW_DUST, behind.add(0, 1, 0), 20, 0.4, 0.02);
        sound(c.level(), behind, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.6f);
        return true;
    }

    public static boolean bladeStorm(SkillContext c) {
        ServerPlayer p = c.player();
        double dmg = melee(p) * c.scale(0.6, 0.1);
        iframes(c, 18);
        int edge = c.data().skillRank("storm_edge"); // more turns, and the storm draws enemies in
        Scheduler.repeat(6 + edge, 3, i -> {
            if (!alive(p)) return;
            swing(p);
            for (LivingEntity t : enemiesAround(p, p.position(), 3.5 + 0.5 * edge)) {
                hitPhysical(p, t, dmg);
                if (edge > 0) pull(t, p.position(), 0.25);
            }
            ring(p.serverLevel(), ParticleTypes.SWEEP_ATTACK, p.position().add(0, 1, 0), 2.5, 8);
            sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.0f + i * 0.2f);
        });
        return true;
    }

    public static boolean meteor(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 at = lookPoint(p, 30);
        int mod = SkillMods.of(c.data(), "meteor"); // 1 Ice Comet: freezes instead of burning; 2 Starfall: later and heavier
        double dmg = spell(c) * c.scale(2.5, 0.4) * attunement(c) * (mod == 2 ? 1.5 : 1.0);
        particles(c.level(), ParticleTypes.FLAME, at.add(0, 8, 0), 40, 0.5, 0.05);
        ring(c.level(), ParticleTypes.FLAME, at.add(0, 0.2, 0), 4, 40);
        sound(c.level(), at, SoundEvents.BLAZE_SHOOT, 1.2f, 0.5f);
        Scheduler.later(mod == 2 ? 30 : 15, () -> {
            if (!alive(p)) return;
            for (LivingEntity t : enemiesAround(p, at, 4)) {
                hitMagic(p, t, dmg);
                if (mod == 1) {
                    debuff(p, t, MobEffects.MOVEMENT_SLOWDOWN, 80, 2);
                    t.setTicksFrozen(Math.max(t.getTicksFrozen(), 160));
                } else {
                    t.igniteForSeconds(5);
                }
                knockback(t, at, 0.8);
            }
            particles(p.serverLevel(), ParticleTypes.EXPLOSION_EMITTER, at, 1, 0, 0);
            particles(p.serverLevel(), ParticleTypes.LAVA, at, 30, 2, 0);
            sound(p.serverLevel(), at, SoundEvents.DRAGON_FIREBALL_EXPLODE, 1.5f, 0.6f);
        });
        int shower = c.data().skillRank("meteor_shower"); // smaller meteors fall around the first one
        for (int i = 0; i < shower; i++) {
            double a = Math.PI * 2 * i / shower + p.getRandom().nextDouble();
            Vec3 small = at.add(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5);
            Scheduler.later(25 + 8 * i, () -> {
                if (!alive(p)) return;
                for (LivingEntity t : enemiesAround(p, small, 2.5)) {
                    hitMagic(p, t, dmg * 0.4);
                    t.igniteForSeconds(3);
                }
                particles(p.serverLevel(), ParticleTypes.EXPLOSION, small, 1, 0, 0);
                particles(p.serverLevel(), ParticleTypes.FLAME, small, 20, 1, 0.05);
                sound(p.serverLevel(), small, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.2f);
            });
        }
        return true;
    }

    public static boolean gravityWell(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 at = lookPoint(p, 24);
        int mod = SkillMods.of(c.data(), "gravity_well"); // 1 Collapse: shorter, stronger pull and damage; 2 Stasis: holds and weakens, less damage
        double dmg = spell(c) * c.scale(0.5, 0.08) * (mod == 1 ? 1.5 : mod == 2 ? 0.5 : 1.0);
        Scheduler.repeat(mod == 1 ? 5 : 10, 4, i -> {
            if (!alive(p)) return;
            for (LivingEntity t : enemiesAround(p, at, 6)) {
                Vec3 pull = at.subtract(t.position());
                if (pull.lengthSqr() > 0.5) {
                    t.setDeltaMovement(t.getDeltaMovement().add(pull.normalize().scale(mod == 1 ? 0.8 : 0.5)));
                    t.hurtMarked = true;
                }
                hitMagic(p, t, dmg);
                debuff(p, t, MobEffects.MOVEMENT_SLOWDOWN, 30, mod == 2 ? 4 : 2);
                if (mod == 2) debuff(p, t, MobEffects.WEAKNESS, 30, 0);
            }
            particles(p.serverLevel(), ParticleTypes.REVERSE_PORTAL, at, 50, 2, 0.1);
            sound(p.serverLevel(), at, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 0.5f);
        });
        int horizon = c.data().skillRank("event_horizon"); // the well collapses with a burst at the end
        if (horizon > 0) {
            double burst = spell(c) * (0.5 + 0.3 * horizon);
            Scheduler.later(42, () -> {
                if (!alive(p)) return;
                for (LivingEntity t : enemiesAround(p, at, 6)) {
                    hitMagic(p, t, burst);
                    knockback(t, at, 1.0);
                }
                particles(p.serverLevel(), ParticleTypes.PORTAL, at, 120, 0.5, 2.0);
                particles(p.serverLevel(), ParticleTypes.EXPLOSION, at, 2, 0.5, 0);
                sound(p.serverLevel(), at, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1f, 0.6f);
            });
        }
        return true;
    }

    public static boolean aegis(SkillContext c) {
        ServerPlayer p = c.player();
        for (LivingEntity a : alliesAround(p, p.position(), 8)) {
            buff(p, a, MobEffects.DAMAGE_RESISTANCE, 120, 1);
            buff(p, a, MobEffects.ABSORPTION, 200, Math.min(3, c.rank() / 2 + 1));
        }
        if (c.data().skillRank("mirror_aegis") > 0) state(c.data(), c.level(), "mirror_aegis", 120); // read by CombatEvents
        ring(c.level(), HOLY_DUST, p.position().add(0, 1, 0), 8, 60);
        sound(c.level(), p.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.2f);
        return true;
    }

    public static boolean seismicCharge(SkillContext c) {
        ServerPlayer p = c.player();
        dash(p, horizontalLook(p), c.scale(2.0, 0.15), 0.05);
        iframes(c, 8);
        double dmg = melee(p) * c.scale(1.2, 0.2);
        Set<LivingEntity> hit = new HashSet<>();
        Scheduler.repeat(2, 5, i -> {
            if (!alive(p)) return;
            for (LivingEntity t : enemiesAround(p, p.position(), 2)) {
                if (!hit.add(t)) continue;
                hitPhysical(p, t, dmg);
                knockback(t, p.position(), 1.5);
                launch(t, 0.3);
                int unstoppable = c.data().skillRank("unstoppable");
                if (unstoppable > 0 && !com.vladosiwe.solostatus.combat.Threat.isBoss(t)) stun(p, t, 10 * unstoppable);
            }
            particles(p.serverLevel(), ParticleTypes.CLOUD, p.position(), 5, 0.4, 0.02);
        });
        int unstoppable = c.data().skillRank("unstoppable");
        if (unstoppable > 0) buff(p, p, MobEffects.DAMAGE_RESISTANCE, 40 + 20 * unstoppable, 0);
        sound(c.level(), p.position(), SoundEvents.IRON_GOLEM_ATTACK, 1f, 0.5f);
        return true;
    }

    public static boolean sniperShot(SkillContext c) {
        ServerPlayer p = c.player();
        var arrow = arrow(p, 0, 0, 5.5f, 10.0 * c.scale(1.0, 0.2), true);
        arrow.setNoGravity(true);
        int killshot = c.data().skillRank("killshot"); // more against wounded targets (CombatEvents)
        if (killshot > 0) arrow.getPersistentData().putInt("solostatus_killshot", killshot);
        particles(c.level(), ParticleTypes.END_ROD, p.getEyePosition().add(p.getLookAngle()), 8, 0.05, 0.05);
        sound(c.level(), p.position(), SoundEvents.CROSSBOW_SHOOT, 1.2f, 0.5f);
        return true;
    }

    public static boolean evasiveRoll(SkillContext c) {
        ServerPlayer p = c.player();
        dash(p, horizontalLook(p).scale(-1), c.scale(1.3, 0.1), 0.35);
        iframes(c, 10);
        int volley = c.data().skillRank("volley_roll");
        for (int i = -1 - volley; i <= 1 + volley; i++) arrow(p, i * (8f - volley), 0, 3.0f, 4.0 * c.scale(1.0, 0.1), false);
        if (volley > 0) buff(p, p, MobEffects.MOVEMENT_SPEED, 40, 1);
        if (c.data().skillRank("wind_runner") > 0) state(c.data(), c.level(), "wind_runner", 60);
        sound(c.level(), p.position(), SoundEvents.ARROW_SHOOT, 1f, 1.4f);
        return true;
    }

    public static boolean holyLight(SkillContext c) {
        ServerPlayer p = c.player();
        int radiance = c.data().skillRank("radiance"); // the light also washes away harm
        for (LivingEntity a : alliesAround(p, p.position(), 8)) {
            heal(p, a, c.scale(6, 2));
            if (radiance > 0) {
                for (var e : new ArrayList<>(a.getActiveEffects())) {
                    if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) a.removeEffect(e.getEffect());
                }
                if (radiance >= 2) buff(p, a, MobEffects.REGENERATION, 40 * radiance, 0);
            }
        }
        double dmg = spell(c) * c.scale(1.5, 0.2);
        for (LivingEntity e : enemiesAround(p, p.position(), 8)) {
            if (e.getType().is(EntityTypeTags.UNDEAD)) {
                hitMagic(p, e, dmg);
                e.igniteForSeconds(3);
            }
        }
        particles(c.level(), HOLY_DUST, p.position().add(0, 2, 0), 60, 3, 0.05);
        ring(c.level(), ParticleTypes.END_ROD, p.position().add(0, 0.2, 0), 8, 60);
        sound(c.level(), p.position(), SoundEvents.BELL_RESONATE, 1f, 1.6f);
        return true;
    }

    public static boolean hex(SkillContext c) {
        ServerPlayer p = c.player();
        LivingEntity t = raycast(p, 24, false);
        if (t == null) return false;
        mark(t, "hex", p, 160);
        debuff(p, t, MobEffects.WEAKNESS, 160, 0);
        debuff(p, t, MobEffects.MOVEMENT_SLOWDOWN, 160, 1);
        debuff(p, t, MobEffects.GLOWING, 160, 0);
        line(c.level(), ParticleTypes.WITCH, p.getEyePosition(), t.getBoundingBox().getCenter(), 0.4);
        sound(c.level(), t.position(), SoundEvents.EVOKER_PREPARE_ATTACK, 1f, 1.2f);
        return true;
    }

    // =============================== THIRD SPECIALIZATIONS ===============================

    /** Duelist: challenges one enemy. Against it: more damage dealt, less taken (CombatEvents reads the mark). */
    public static boolean duelChallenge(SkillContext c) {
        ServerPlayer p = c.player();
        LivingEntity t = raycast(p, 16, false);
        if (t == null) return false;
        int ticks = (int) c.scale(200, 20);
        mark(t, "duel", p, ticks);
        debuff(p, t, MobEffects.GLOWING, ticks, 0);
        line(c.level(), ParticleTypes.CRIT, p.getEyePosition(), t.getBoundingBox().getCenter(), 0.3);
        ring(c.level(), ParticleTypes.ENCHANTED_HIT, t.position().add(0, 0.1, 0), 1.2, 16);
        sound(c.level(), p.position(), SoundEvents.ANVIL_PLACE, 0.6f, 1.6f);
        return true;
    }

    /** Venomancer: a cloud of poison around the hunter. */
    public static boolean toxicCloud(SkillContext c) {
        ServerPlayer p = c.player();
        double r = c.scale(4, 0.5);
        int amp = c.rank() >= 4 ? 2 : 1;
        for (LivingEntity t : enemiesAround(p, p.position(), r)) {
            debuff(p, t, MobEffects.POISON, (int) c.scale(100, 20), amp);
            debuff(p, t, MobEffects.MOVEMENT_SLOWDOWN, 80, 0);
            mark(t, "venom", p, 160);
        }
        int virulent = c.data().skillRank("virulent_cloud"); // the cloud stays where it was released
        if (virulent > 0) {
            Vec3 at = p.position();
            int pulses = 1 + virulent;
            Scheduler.repeat(pulses, 20, i -> {
                if (!alive(p)) return;
                for (LivingEntity t : enemiesAround(p, at, r)) {
                    debuff(p, t, MobEffects.POISON, 60, amp);
                    mark(t, "venom", p, 160);
                }
                particles(p.serverLevel(), ParticleTypes.ITEM_SLIME, at.add(0, 0.5, 0), 30, r * 0.6, 0.02);
            });
        }
        particles(c.level(), ParticleTypes.ITEM_SLIME, p.position().add(0, 0.5, 0), 60, r * 0.6, 0.02);
        particles(c.level(), ParticleTypes.SNEEZE, p.position().add(0, 0.6, 0), 40, r * 0.6, 0.01);
        sound(c.level(), p.position(), SoundEvents.BREWING_STAND_BREW, 1f, 0.6f);
        return true;
    }

    /** Spellblade: for a few seconds melee hits carry magic and give mana back (CombatEvents). */
    public static boolean arcaneStrike(SkillContext c) {
        state(c.data(), c.level(), "arcane_strike", (int) c.scale(120, 20));
        ring(c.level(), ParticleTypes.WITCH, c.player().position().add(0, 1, 0), 1, 20);
        sound(c.level(), c.player().position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1f, 1.3f);
        return true;
    }

    /** Avenger: releases the damage stored by Vengeance around the hunter. */
    public static boolean retribution(SkillContext c) {
        ServerPlayer p = c.player();
        com.vladosiwe.solostatus.data.PlayerData d = c.data();
        if (d.vengeance < 2) return false; // nothing taken yet: nothing to give back
        double dmg = d.vengeance * c.scale(1.0, 0.2);
        d.vengeance = 0;
        int wrath = d.skillRank("wrath_unleashed"); // the release stuns, and some of it comes back as health
        for (LivingEntity t : enemiesAround(p, p.position(), 5)) {
            hitPhysical(p, t, dmg);
            launch(t, 0.4);
            if (wrath > 0 && !com.vladosiwe.solostatus.combat.Threat.isBoss(t)) stun(p, t, 10 * wrath);
        }
        if (wrath > 0) heal(p, p, dmg * 0.15 * wrath);
        particles(c.level(), ParticleTypes.FLAME, p.position().add(0, 1, 0), 60, 2, 0.1);
        ring(c.level(), ParticleTypes.LAVA, p.position().add(0, 0.2, 0), 4, 30);
        sound(c.level(), p.position(), SoundEvents.BLAZE_SHOOT, 1f, 0.6f);
        return true;
    }

    /** Crossbowman: a quick burst of bolts. */
    public static boolean boltBarrage(SkillContext c) {
        ServerPlayer p = c.player();
        int bolts = 3 + c.rank();
        double dmg = 4.0 * c.scale(1.0, 0.15);
        int finale = c.data().skillRank("explosive_finale"); // the last bolt bursts on impact (CombatEvents)
        Scheduler.repeat(3, bolts, i -> {
            if (!p.isAlive()) return;
            var bolt = arrow(p, (i % 2 == 0 ? 1 : -1) * i * 1.2f, 0, 3.6f, dmg, false);
            if (finale > 0 && i == bolts - 1) bolt.getPersistentData().putDouble("solostatus_explode", dmg * (1.0 + 0.5 * finale));
            sound(c.level(), p.position(), SoundEvents.CROSSBOW_SHOOT, 0.8f, 1.2f + i * 0.05f);
        });
        return true;
    }

    /** Bard: a hymn that strengthens allies and weakens enemies around. */
    public static boolean battleHymn(SkillContext c) {
        ServerPlayer p = c.player();
        int encore = c.data().skillRank("encore"); // a longer hymn that also steadies allies
        int dur = (int) (c.scale(200, 30) * (1 + 0.25 * encore));
        state(c.data(), c.level(), "battle_hymn", dur); // read by Song of Valor
        for (LivingEntity a : alliesAround(p, p.position(), 10)) {
            if (encore > 0) buff(p, a, MobEffects.DAMAGE_RESISTANCE, dur, 0);
            buff(p, a, MobEffects.MOVEMENT_SPEED, dur, 0);
            buff(p, a, MobEffects.DAMAGE_BOOST, dur, c.rank() >= 3 ? 1 : 0);
            buff(p, a, MobEffects.REGENERATION, dur / 2, 0);
        }
        buff(p, p, MobEffects.DAMAGE_BOOST, dur, c.rank() >= 3 ? 1 : 0);
        for (LivingEntity t : enemiesAround(p, p.position(), 10)) {
            debuff(p, t, MobEffects.WEAKNESS, dur, 0);
            debuff(p, t, MobEffects.MOVEMENT_SLOWDOWN, dur / 2, 0);
        }
        particles(c.level(), ParticleTypes.NOTE, p.position().add(0, 2, 0), 30, 2.5, 1);
        sound(c.level(), p.position(), SoundEvents.NOTE_BLOCK_CHIME.value(), 1f, 1f);
        sound(c.level(), p.position(), SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1.5f);
        return true;
    }

    // =============================== AWAKENINGS ===============================

    public static boolean sprint(SkillContext c) {
        buff(c.player(), c.player(), MobEffects.MOVEMENT_SPEED, (int) c.scale(100, 20), c.rank() >= 4 ? 2 : 1);
        particles(c.level(), ParticleTypes.CLOUD, c.player().position(), 10, 0.3, 0.05);
        sound(c.level(), c.player().position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.6f, 1.6f);
        return true;
    }

    public static boolean bloodlust(SkillContext c) {
        ServerPlayer p = c.player();
        double r = c.scale(6, 1);
        List<LivingEntity> targets = enemiesAround(p, p.position(), r);
        for (LivingEntity t : targets) {
            debuff(p, t, MobEffects.MOVEMENT_SLOWDOWN, 80, 1);
            debuff(p, t, MobEffects.WEAKNESS, 80, 0);
            if (t instanceof Mob m && m.getMaxHealth() < p.getMaxHealth() * 0.5) m.setTarget(null);
        }
        ring(c.level(), BLOOD_DUST, p.position().add(0, 0.5, 0), r, 50);
        sound(c.level(), p.position(), SoundEvents.WARDEN_HEARTBEAT, 2f, 0.8f);
        return true;
    }

    public static boolean crossSlash(SkillContext c) {
        ServerPlayer p = c.player();
        if (closestInCone(p, 3.5, 90) == null) return false;
        double dmg = melee(p) * c.scale(0.9, 0.12);
        Scheduler.repeat(4, 2, i -> {
            if (!alive(p)) return;
            swing(p);
            for (LivingEntity t : enemiesInCone(p, 3.5, 90)) hitPhysical(p, t, dmg);
            particles(p.serverLevel(), ParticleTypes.SWEEP_ATTACK, p.getEyePosition().add(p.getLookAngle().scale(2)), 2, 0.4, 0);
            sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.1f + i * 0.2f);
        });
        if (com.vladosiwe.solostatus.mastery.MasteryTechniques.mastered(c.data(), com.vladosiwe.solostatus.combat.WeaponType.SWORD)) { // sword mastery 50: a wide finishing slash closes the cross
            Scheduler.later(8, () -> {
                if (!alive(p)) return;
                swing(p);
                for (LivingEntity t : enemiesInCone(p, 4.5, 140)) hitPhysical(p, t, dmg * 1.5);
                particles(p.serverLevel(), ParticleTypes.SWEEP_ATTACK, p.getEyePosition().add(p.getLookAngle().scale(2)), 5, 1.2, 0);
                sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.8f);
            });
        }
        return true;
    }


    public static boolean vitalStrike(SkillContext c) {
        state(c.data(), c.level(), "vital_strike", 120);
        particles(c.level(), ParticleTypes.CRIT, c.player().position().add(0, 1, 0), 15, 0.3, 0.1);
        sound(c.level(), c.player().position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.6f, 1.8f);
        return true;
    }

    public static boolean sunder(SkillContext c) {
        ServerPlayer p = c.player();
        LivingEntity t = closestInCone(p, 3.5, 70);
        if (t == null) return false;
        swing(p);
        hitPhysical(p, t, melee(p) * c.scale(1.2, 0.15));
        mark(t, "sunder", p, 160);
        if (com.vladosiwe.solostatus.mastery.MasteryTechniques.mastered(c.data(), com.vladosiwe.solostatus.combat.WeaponType.AXE)) { // axe mastery 50: the split spreads to those around
            for (LivingEntity e : enemiesAround(p, t.position(), 3)) if (e != t) mark(e, "sunder", p, 160);
            ring(c.level(), ParticleTypes.DAMAGE_INDICATOR, t.position().add(0, 0.5, 0), 3, 16);
        }
        particles(c.level(), ParticleTypes.DAMAGE_INDICATOR, t.getBoundingBox().getCenter(), 8, 0.3, 0.1);
        sound(c.level(), t.position(), SoundEvents.ANVIL_LAND, 0.5f, 1.3f);
        return true;
    }

    public static boolean piercingThrust(SkillContext c) {
        ServerPlayer p = c.player();
        boolean mastered = com.vladosiwe.solostatus.mastery.MasteryTechniques.mastered(c.data(), com.vladosiwe.solostatus.combat.WeaponType.SPEAR); // spear mastery 50: a longer thrust that pins
        double reach = mastered ? 9 : 6;
        List<LivingEntity> line = enemiesInLine(p, reach, 0.8);
        swing(p);
        double dmg = melee(p) * c.scale(1.3, 0.15);
        for (LivingEntity t : line) {
            hitPhysical(p, t, dmg);
            if (mastered) debuff(p, t, MobEffects.MOVEMENT_SLOWDOWN, 60, 3);
        }
        line(c.level(), ParticleTypes.CRIT, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(reach)), 0.3);
        sound(c.level(), p.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1.3f);
        return !line.isEmpty();
    }

    public static boolean groundPound(SkillContext c) {
        ServerPlayer p = c.player();
        swing(p);
        double dmg = melee(p) * c.scale(1.2, 0.2);
        boolean mastered = com.vladosiwe.solostatus.mastery.MasteryTechniques.mastered(c.data(), com.vladosiwe.solostatus.combat.WeaponType.MACE); // mace mastery 50: the blow throws up and dazes
        for (LivingEntity t : enemiesAround(p, p.position(), 3)) {
            hitPhysical(p, t, dmg);
            debuff(p, t, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
            if (mastered && !com.vladosiwe.solostatus.combat.Threat.isBoss(t)) {
                launch(t, 0.5);
                stun(p, t, 20);
            }
        }
        ring(c.level(), ParticleTypes.CLOUD, p.position().add(0, 0.1, 0), 3, 30);
        sound(c.level(), p.position(), SoundEvents.MACE_SMASH_GROUND, 1f, 1f);
        return true;
    }

    // ---------------- Пробуждения второго круга (мастерство 25) ----------------

    public static boolean bladeFlash(SkillContext c) {
        ServerPlayer p = c.player();
        List<LivingEntity> line = enemiesInLine(p, 6, 1.0);
        Vec3 start = p.position().add(0, 1, 0);
        dash(p, horizontalLook(p), c.scale(2.0, 0.1), 0.05);
        iframes(c, 8);
        double dmg = melee(p) * c.scale(1.5, 0.2);
        for (LivingEntity t : line) hitPhysical(p, t, dmg);
        line(c.level(), ParticleTypes.SWEEP_ATTACK, start, start.add(horizontalLook(p).scale(6)), 1.0);
        line(c.level(), SYSTEM_DUST, start, start.add(horizontalLook(p).scale(6)), 0.3);
        sound(c.level(), p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.6f);
        return true;
    }




    public static boolean frostArrow(SkillContext c) {
        ServerPlayer p = c.player();
        var arrow = arrow(p, 0, 0, 3.2f, 5.0 * c.scale(1.0, 0.1), false);
        arrow.getPersistentData().putInt("solostatus_freeze", c.rank());
        if (com.vladosiwe.solostatus.mastery.MasteryTechniques.mastered(c.data(), com.vladosiwe.solostatus.combat.WeaponType.BOW)) arrow.getPersistentData().putBoolean("solostatus_freeze_area", true); // bow mastery 50
        particles(c.level(), ParticleTypes.SNOWFLAKE, p.getEyePosition().add(p.getLookAngle()), 10, 0.1, 0.02);
        sound(c.level(), p.position(), SoundEvents.ARROW_SHOOT, 1f, 1.5f);
        return true;
    }

    public static boolean explosiveBolt(SkillContext c) {
        ServerPlayer p = c.player();
        var arrow = arrow(p, 0, 0, 3.5f, 4.0, false);
        arrow.getPersistentData().putDouble("solostatus_explode", 5.0 * c.scale(1.0, 0.15));
        if (com.vladosiwe.solostatus.mastery.MasteryTechniques.mastered(c.data(), com.vladosiwe.solostatus.combat.WeaponType.CROSSBOW)) arrow.getPersistentData().putBoolean("solostatus_explode_big", true); // crossbow mastery 50
        sound(c.level(), p.position(), SoundEvents.CROSSBOW_SHOOT, 1f, 0.6f);
        return true;
    }


    public static boolean arcaneBlast(SkillContext c) {
        ServerPlayer p = c.player();
        double dmg = spell(c) * c.scale(1.2, 0.2);
        boolean mastered = com.vladosiwe.solostatus.mastery.MasteryTechniques.mastered(c.data(), com.vladosiwe.solostatus.combat.WeaponType.STAFF); // staff mastery 50: a wider blast that echoes
        double r = mastered ? 6 : 4;
        for (LivingEntity t : enemiesAround(p, p.position(), r)) {
            hitMagic(p, t, dmg);
            knockback(t, p.position(), 1.2);
        }
        if (mastered) {
            Vec3 at = p.position();
            Scheduler.later(20, () -> {
                if (!alive(p)) return;
                for (LivingEntity t : enemiesAround(p, at, r)) hitMagic(p, t, dmg * 0.5);
                ring(p.serverLevel(), SYSTEM_DUST, at.add(0, 1, 0), r, 40);
                sound(p.serverLevel(), at, SoundEvents.EVOKER_CAST_SPELL, 0.8f, 1.2f);
            });
        }
        ring(c.level(), SYSTEM_DUST, p.position().add(0, 1, 0), 4, 40);
        particles(c.level(), ParticleTypes.ENCHANT, p.position().add(0, 1, 0), 60, 2, 0.5);
        sound(c.level(), p.position(), SoundEvents.EVOKER_CAST_SPELL, 1f, 0.8f);
        return true;
    }

    public static boolean manaBlade(SkillContext c) {
        state(c.data(), c.level(), "mana_blade", 160);
        c.data().manaBladeCharges = 4 + c.rank();
        particles(c.level(), SYSTEM_DUST, c.player().position().add(0, 1.2, 0), 30, 0.4, 0.02);
        sound(c.level(), c.player().position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 1.4f);
        return true;
    }

    /** Скрытый навык: призыв теневых волков-союзников на 30 секунд. */
    public static boolean shadowLegion(SkillContext c) {
        ServerPlayer p = c.player();
        int count = 2 + c.rank() / 2;
        for (int i = 0; i < count; i++) {
            net.minecraft.world.entity.animal.Wolf wolf = net.minecraft.world.entity.EntityType.WOLF.create(c.level());
            if (wolf == null) continue;
            double a = Math.PI * 2 * i / count;
            wolf.moveTo(p.getX() + Math.cos(a) * 1.5, p.getY(), p.getZ() + Math.sin(a) * 1.5, p.getYRot(), 0);
            wolf.tame(p);
            wolf.setCustomName(net.minecraft.network.chat.Component.translatable("solostatus.summon.shadow"));
            wolf.getPersistentData().putBoolean(com.vladosiwe.solostatus.combat.CombatEvents.SUMMON_TAG, true);
            wolf.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, c.rank() >= 3 ? 1 : 0, false, false));
            wolf.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 1, false, false));
            wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1, false, false));
            c.level().addFreshEntity(wolf);
            particles(c.level(), SHADOW_DUST, wolf.position().add(0, 0.5, 0), 25, 0.4, 0.02);
            Scheduler.later(600, () -> {
                if (wolf.isAlive()) {
                    particles((ServerLevel) wolf.level(), SHADOW_DUST, wolf.position().add(0, 0.5, 0), 20, 0.3, 0.02);
                    wolf.discard();
                }
            });
        }
        sound(c.level(), p.position(), SoundEvents.WITHER_SPAWN, 0.4f, 1.6f);
        return true;
    }

    public static boolean rulerGrip(SkillContext c) {
        ServerPlayer p = c.player();
        LivingEntity t = raycast(p, 20, false);
        if (t == null) return false;
        double dmg = spell(c) * c.scale(2.0, 0.4);
        t.setDeltaMovement(0, 1.0, 0);
        t.hurtMarked = true;
        line(c.level(), SYSTEM_DUST, p.getEyePosition(), t.getBoundingBox().getCenter(), 0.3);
        sound(c.level(), t.position(), SoundEvents.EVOKER_CAST_SPELL, 1f, 0.5f);
        Scheduler.later(12, () -> {
            if (!alive(p) || !t.isAlive()) return;
            t.setDeltaMovement(0, -2.2, 0);
            t.hurtMarked = true;
            Scheduler.later(5, () -> {
                if (!alive(p) || !t.isAlive()) return;
                hitMagic(p, t, dmg);
                particles(p.serverLevel(), ParticleTypes.EXPLOSION, t.position(), 2, 0.3, 0);
                sound(p.serverLevel(), t.position(), SoundEvents.ANVIL_LAND, 1f, 0.4f);
            });
        });
        return true;
    }
}
