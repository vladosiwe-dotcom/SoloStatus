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
        double dmg = melee(p) * c.scale(1.0, 0.15);
        for (LivingEntity t : enemiesAround(p, p.position(), 3.5)) {
            hitPhysical(p, t, dmg);
            knockback(t, p.position(), 0.5);
        }
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
        hitPhysical(p, t, melee(p) * c.scale(3.0, 0.4));
        stun(p, t, 30);
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
        int hits = 4 + c.rank() / 2;
        double dmg = melee(p) * c.scale(0.45, 0.05);
        Scheduler.repeat(3, hits, i -> {
            if (!alive(p) || !t.isAlive() || t.distanceToSqr(p) > 25) return;
            swing(p);
            hitPhysical(p, t, dmg);
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
        swing(p);
        hitPhysical(p, t, melee(p) * mult);
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

    public static boolean manaBolt(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 from = p.getEyePosition();
        LivingEntity t = raycast(p, 24, false);
        Vec3 to = t != null ? t.getBoundingBox().getCenter() : lookPoint(p, 24);
        line(c.level(), SYSTEM_DUST, from, to, 0.4);
        sound(c.level(), from, SoundEvents.EVOKER_CAST_SPELL, 0.7f, 1.8f);
        if (t == null) return true;
        hitMagic(p, t, spell(c) * c.scale(1.0, 0.15));
        particles(c.level(), ParticleTypes.ENCHANTED_HIT, to, 12, 0.2, 0.2);
        return true;
    }

    public static boolean frostNova(SkillContext c) {
        ServerPlayer p = c.player();
        double radius = c.scale(4.0, 0.3);
        double dmg = spell(c) * 0.8 * attunement(c);
        for (LivingEntity t : enemiesAround(p, p.position(), radius)) {
            hitMagic(p, t, dmg);
            debuff(p, t, MobEffects.MOVEMENT_SLOWDOWN, 60, 2);
            t.setTicksFrozen(Math.max(t.getTicksFrozen(), 160));
        }
        ring(c.level(), ParticleTypes.SNOWFLAKE, p.position().add(0, 0.3, 0), radius, 40);
        particles(c.level(), ParticleTypes.SNOWFLAKE, p.position().add(0, 1, 0), 40, radius / 2, 0.05);
        sound(c.level(), p.position(), SoundEvents.GLASS_BREAK, 1f, 0.6f);
        sound(c.level(), p.position(), SoundEvents.PLAYER_HURT_FREEZE, 1f, 1f);
        return true;
    }

    public static boolean flameBurst(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 at = lookPoint(p, 20);
        double dmg = spell(c) * c.scale(1.4, 0.2) * attunement(c);
        for (LivingEntity t : enemiesAround(p, at, 3)) {
            hitMagic(p, t, dmg);
            t.igniteForSeconds(4);
        }
        line(c.level(), ParticleTypes.FLAME, p.getEyePosition(), at, 0.6);
        particles(c.level(), ParticleTypes.FLAME, at, 60, 1.2, 0.08);
        particles(c.level(), ParticleTypes.LAVA, at, 8, 0.8, 0);
        sound(c.level(), at, SoundEvents.FIRECHARGE_USE, 1f, 0.8f);
        return true;
    }

    public static boolean chainLightning(SkillContext c) {
        ServerPlayer p = c.player();
        LivingEntity first = raycast(p, 20, false);
        if (first == null) return false;
        int jumps = 3 + c.rank();
        double dmg = spell(c) * 1.2 * attunement(c);
        Set<LivingEntity> hit = new HashSet<>();
        LivingEntity current = first;
        Vec3 from = p.getEyePosition();
        for (int i = 0; i < jumps && current != null; i++) {
            hit.add(current);
            Vec3 to = current.getBoundingBox().getCenter();
            line(c.level(), ParticleTypes.ELECTRIC_SPARK, from, to, 0.3);
            hitMagic(p, current, dmg * Math.pow(0.85, i));
            from = to;
            LivingEntity next = null;
            double best = 36;
            for (LivingEntity e : enemiesAround(p, to, 6)) {
                if (hit.contains(e)) continue;
                double d = e.distanceToSqr(to);
                if (d < best) { best = d; next = e; }
            }
            current = next;
        }
        sound(c.level(), first.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, 1.4f);
        return true;
    }

    public static boolean manaShield(SkillContext c) {
        ServerPlayer p = c.player();
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, Math.min(4, c.rank()), false, true));
        ring(c.level(), SYSTEM_DUST, p.position().add(0, 1, 0), 1.2, 24);
        sound(c.level(), p.position(), SoundEvents.BEACON_ACTIVATE, 0.7f, 1.6f);
        return true;
    }

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
        LivingEntity t = closestInCone(p, 3.5, 70);
        if (t == null) return false;
        boolean shield = p.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD) || p.getMainHandItem().is(net.minecraft.world.item.Items.SHIELD)
                || p.getOffhandItem().getItem() instanceof net.minecraft.world.item.ShieldItem;
        swing(p);
        hitPhysical(p, t, melee(p) * c.scale(1.0, 0.15) * (shield ? 1.3 : 1.0));
        knockback(t, p.position(), 1.4);
        stun(p, t, 30);
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
        for (LivingEntity a : alliesAround(p, p.position(), 8)) {
            buff(p, a, MobEffects.DAMAGE_RESISTANCE, 160, c.rank() >= 4 ? 1 : 0);
            buff(p, a, MobEffects.ABSORPTION, 160, 0);
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
        arrow(p, 0, 0, 3.5f, 6.0 * c.scale(1.2, 0.15), true);
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
        double dmg = 4.0 * c.scale(1.0, 0.1);
        Scheduler.repeat(2, n, i -> {
            if (!alive(p)) return;
            double a = p.getRandom().nextDouble() * Math.PI * 2;
            double r = p.getRandom().nextDouble() * 3;
            skyArrow(p, at.add(Math.cos(a) * r, 0, Math.sin(a) * r), dmg);
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
        heal(p, t, c.scale(4.0, 1.5) * (1 + StatFormulas.magicPower(c.data())));
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
        Scheduler.repeat(20, 8, i -> {
            if (!alive(p)) return;
            ServerLevel level = p.serverLevel();
            for (LivingEntity a : alliesAround(p, at, 6)) heal(p, a, heal);
            for (LivingEntity e : enemiesAround(p, at, 6)) debuff(p, e, MobEffects.MOVEMENT_SLOWDOWN, 30, 0);
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
        particles(c.level(), SHADOW_DUST, behind.add(0, 1, 0), 20, 0.4, 0.02);
        sound(c.level(), behind, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.6f);
        return true;
    }

    public static boolean bladeStorm(SkillContext c) {
        ServerPlayer p = c.player();
        double dmg = melee(p) * c.scale(0.6, 0.1);
        iframes(c, 18);
        Scheduler.repeat(6, 3, i -> {
            if (!alive(p)) return;
            swing(p);
            for (LivingEntity t : enemiesAround(p, p.position(), 3.5)) hitPhysical(p, t, dmg);
            ring(p.serverLevel(), ParticleTypes.SWEEP_ATTACK, p.position().add(0, 1, 0), 2.5, 8);
            sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.0f + i * 0.2f);
        });
        return true;
    }

    public static boolean meteor(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 at = lookPoint(p, 30);
        double dmg = spell(c) * c.scale(2.5, 0.4) * attunement(c);
        particles(c.level(), ParticleTypes.FLAME, at.add(0, 8, 0), 40, 0.5, 0.05);
        ring(c.level(), ParticleTypes.FLAME, at.add(0, 0.2, 0), 4, 40);
        sound(c.level(), at, SoundEvents.BLAZE_SHOOT, 1.2f, 0.5f);
        Scheduler.later(15, () -> {
            if (!alive(p)) return;
            for (LivingEntity t : enemiesAround(p, at, 4)) {
                hitMagic(p, t, dmg);
                t.igniteForSeconds(5);
                knockback(t, at, 0.8);
            }
            particles(p.serverLevel(), ParticleTypes.EXPLOSION_EMITTER, at, 1, 0, 0);
            particles(p.serverLevel(), ParticleTypes.LAVA, at, 30, 2, 0);
            sound(p.serverLevel(), at, SoundEvents.DRAGON_FIREBALL_EXPLODE, 1.5f, 0.6f);
        });
        return true;
    }

    public static boolean gravityWell(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 at = lookPoint(p, 24);
        double dmg = spell(c) * c.scale(0.5, 0.08);
        Scheduler.repeat(10, 4, i -> {
            if (!alive(p)) return;
            for (LivingEntity t : enemiesAround(p, at, 6)) {
                Vec3 pull = at.subtract(t.position());
                if (pull.lengthSqr() > 0.5) {
                    t.setDeltaMovement(t.getDeltaMovement().add(pull.normalize().scale(0.5)));
                    t.hurtMarked = true;
                }
                hitMagic(p, t, dmg);
                debuff(p, t, MobEffects.MOVEMENT_SLOWDOWN, 30, 2);
            }
            particles(p.serverLevel(), ParticleTypes.REVERSE_PORTAL, at, 50, 2, 0.1);
            sound(p.serverLevel(), at, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 0.5f);
        });
        return true;
    }

    public static boolean aegis(SkillContext c) {
        ServerPlayer p = c.player();
        for (LivingEntity a : alliesAround(p, p.position(), 8)) {
            buff(p, a, MobEffects.DAMAGE_RESISTANCE, 120, 1);
            buff(p, a, MobEffects.ABSORPTION, 200, Math.min(3, c.rank() / 2 + 1));
        }
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
            }
            particles(p.serverLevel(), ParticleTypes.CLOUD, p.position(), 5, 0.4, 0.02);
        });
        sound(c.level(), p.position(), SoundEvents.IRON_GOLEM_ATTACK, 1f, 0.5f);
        return true;
    }

    public static boolean sniperShot(SkillContext c) {
        ServerPlayer p = c.player();
        var arrow = arrow(p, 0, 0, 5.5f, 10.0 * c.scale(1.0, 0.2), true);
        arrow.setNoGravity(true);
        particles(c.level(), ParticleTypes.END_ROD, p.getEyePosition().add(p.getLookAngle()), 8, 0.05, 0.05);
        sound(c.level(), p.position(), SoundEvents.CROSSBOW_SHOOT, 1.2f, 0.5f);
        return true;
    }

    public static boolean evasiveRoll(SkillContext c) {
        ServerPlayer p = c.player();
        dash(p, horizontalLook(p).scale(-1), c.scale(1.3, 0.1), 0.35);
        iframes(c, 10);
        for (int i = -1; i <= 1; i++) arrow(p, i * 8f, 0, 3.0f, 4.0 * c.scale(1.0, 0.1), false);
        sound(c.level(), p.position(), SoundEvents.ARROW_SHOOT, 1f, 1.4f);
        return true;
    }

    public static boolean holyLight(SkillContext c) {
        ServerPlayer p = c.player();
        for (LivingEntity a : alliesAround(p, p.position(), 8)) heal(p, a, c.scale(6, 2));
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
        return true;
    }

    public static boolean earthshatter(SkillContext c) {
        ServerPlayer p = c.player();
        swing(p);
        double dmg = melee(p) * c.scale(1.4, 0.2);
        for (LivingEntity t : enemiesAround(p, p.position(), 3.5)) {
            hitPhysical(p, t, dmg);
            launch(t, 0.5);
        }
        particles(c.level(), ParticleTypes.EXPLOSION, p.position().add(horizontalLook(p).scale(1.5)), 2, 0.5, 0);
        sound(c.level(), p.position(), SoundEvents.MACE_SMASH_GROUND, 1f, 0.7f);
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
        particles(c.level(), ParticleTypes.DAMAGE_INDICATOR, t.getBoundingBox().getCenter(), 8, 0.3, 0.1);
        sound(c.level(), t.position(), SoundEvents.ANVIL_LAND, 0.5f, 1.3f);
        return true;
    }

    public static boolean piercingThrust(SkillContext c) {
        ServerPlayer p = c.player();
        List<LivingEntity> line = enemiesInLine(p, 6, 0.8);
        swing(p);
        double dmg = melee(p) * c.scale(1.3, 0.15);
        for (LivingEntity t : line) hitPhysical(p, t, dmg);
        line(c.level(), ParticleTypes.CRIT, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(6)), 0.3);
        sound(c.level(), p.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1.3f);
        return !line.isEmpty();
    }

    public static boolean groundPound(SkillContext c) {
        ServerPlayer p = c.player();
        swing(p);
        double dmg = melee(p) * c.scale(1.2, 0.2);
        for (LivingEntity t : enemiesAround(p, p.position(), 3)) {
            hitPhysical(p, t, dmg);
            debuff(p, t, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
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

    public static boolean rendingCleave(SkillContext c) {
        ServerPlayer p = c.player();
        List<LivingEntity> targets = enemiesInCone(p, 4, 180);
        if (targets.isEmpty()) return false;
        swing(p);
        double dmg = melee(p) * c.scale(1.5, 0.2);
        for (LivingEntity t : targets) {
            hitPhysical(p, t, dmg);
            mark(t, "sunder", p, 120);
        }
        particles(c.level(), ParticleTypes.SWEEP_ATTACK, p.getEyePosition().add(horizontalLook(p).scale(2)), 4, 1.0, 0);
        sound(c.level(), p.position(), SoundEvents.ANVIL_LAND, 0.5f, 1.5f);
        return true;
    }

    public static boolean spearWhirl(SkillContext c) {
        ServerPlayer p = c.player();
        swing(p);
        double dmg = melee(p) * c.scale(1.1, 0.15);
        for (LivingEntity t : enemiesAround(p, p.position(), 4.5)) {
            hitPhysical(p, t, dmg);
            knockback(t, p.position(), 1.0);
        }
        ring(c.level(), ParticleTypes.SWEEP_ATTACK, p.position().add(0, 1, 0), 3.5, 12);
        sound(c.level(), p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.9f);
        return true;
    }

    public static boolean shockwave(SkillContext c) {
        ServerPlayer p = c.player();
        Vec3 at = p.position();
        double dmg = melee(p) * c.scale(1.0, 0.15);
        Set<LivingEntity> hit = new HashSet<>();
        swing(p);
        Scheduler.repeat(4, 3, i -> {
            if (!alive(p)) return;
            double r = 2 + 2 * i;
            for (LivingEntity t : enemiesAround(p, at, r)) {
                if (!hit.add(t)) continue;
                hitPhysical(p, t, dmg);
                launch(t, 0.35);
            }
            ring(p.serverLevel(), ParticleTypes.CLOUD, at.add(0, 0.1, 0), r, 24 + 8 * i);
            sound(p.serverLevel(), at, SoundEvents.MACE_SMASH_GROUND, 0.8f, 0.8f + i * 0.2f);
        });
        return true;
    }

    public static boolean frostArrow(SkillContext c) {
        ServerPlayer p = c.player();
        var arrow = arrow(p, 0, 0, 3.2f, 5.0 * c.scale(1.0, 0.1), false);
        arrow.getPersistentData().putInt("solostatus_freeze", c.rank());
        particles(c.level(), ParticleTypes.SNOWFLAKE, p.getEyePosition().add(p.getLookAngle()), 10, 0.1, 0.02);
        sound(c.level(), p.position(), SoundEvents.ARROW_SHOOT, 1f, 1.5f);
        return true;
    }

    public static boolean explosiveBolt(SkillContext c) {
        ServerPlayer p = c.player();
        var arrow = arrow(p, 0, 0, 3.5f, 4.0, false);
        arrow.getPersistentData().putDouble("solostatus_explode", 5.0 * c.scale(1.0, 0.15));
        sound(c.level(), p.position(), SoundEvents.CROSSBOW_SHOOT, 1f, 0.6f);
        return true;
    }

    public static boolean shieldCharge(SkillContext c) {
        ServerPlayer p = c.player();
        dash(p, horizontalLook(p), c.scale(1.8, 0.1), 0.05);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1, false, false));
        double dmg = melee(p) * c.scale(1.0, 0.15);
        Set<LivingEntity> hit = new HashSet<>();
        Scheduler.repeat(2, 5, i -> {
            if (!alive(p)) return;
            for (LivingEntity t : enemiesAround(p, p.position(), 2)) {
                if (!hit.add(t)) continue;
                hitPhysical(p, t, dmg);
                knockback(t, p.position(), 1.6);
                stun(p, t, 20);
            }
        });
        sound(c.level(), p.position(), SoundEvents.SHIELD_BLOCK, 1f, 0.5f);
        return true;
    }

    public static boolean arcaneBlast(SkillContext c) {
        ServerPlayer p = c.player();
        double dmg = spell(c) * c.scale(1.2, 0.2);
        for (LivingEntity t : enemiesAround(p, p.position(), 4)) {
            hitMagic(p, t, dmg);
            knockback(t, p.position(), 1.2);
        }
        ring(c.level(), SYSTEM_DUST, p.position().add(0, 1, 0), 4, 40);
        particles(c.level(), ParticleTypes.ENCHANT, p.position().add(0, 1, 0), 60, 2, 0.5);
        sound(c.level(), p.position(), SoundEvents.EVOKER_CAST_SPELL, 1f, 0.8f);
        return true;
    }

    public static boolean fistBarrage(SkillContext c) {
        ServerPlayer p = c.player();
        LivingEntity t = closestInCone(p, 3.0, 70);
        if (t == null) return false;
        double dmg = melee(p) * c.scale(0.4, 0.05);
        Scheduler.repeat(2, 6, i -> {
            if (!alive(p) || !t.isAlive() || t.distanceToSqr(p) > 16) return;
            swing(p);
            hitPhysical(p, t, dmg);
            particles(p.serverLevel(), ParticleTypes.CRIT, t.getBoundingBox().getCenter(), 5, 0.2, 0.2);
            sound(p.serverLevel(), t.position(), SoundEvents.PLAYER_ATTACK_WEAK, 0.7f, 1.2f + i * 0.08f);
        });
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
