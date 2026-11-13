package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.clazz.Specialization;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Общие строительные блоки для эффектов навыков (только сервер).
 * Все попадания, урон и перемещения считаются на сервере; клиент получает частицы, звуки и движение
 * через стандартную синхронизацию сущностей.
 */
public final class SkillFx {
    /** Голографическая синяя пыль системы. */
    public static final DustParticleOptions SYSTEM_DUST = new DustParticleOptions(new Vector3f(0.35f, 0.75f, 1.0f), 1.2f);
    public static final DustParticleOptions BLOOD_DUST = new DustParticleOptions(new Vector3f(0.8f, 0.05f, 0.05f), 1.2f);
    public static final DustParticleOptions SHADOW_DUST = new DustParticleOptions(new Vector3f(0.25f, 0.05f, 0.4f), 1.4f);
    public static final DustParticleOptions HOLY_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.4f), 1.2f);

    /** true, пока урон наносится навыком — CombatEvents не засчитывает такой урон как удар оружием для комбо. */
    public static boolean applyingSkillDamage = false;

    private SkillFx() {}

    // ========================= Цели =========================

    public static boolean ownedBy(Entity e, Player p) {
        return e instanceof OwnableEntity own && Objects.equals(own.getOwnerUUID(), p.getUUID());
    }

    /** Цель для навыков по площади: враждебные существа и те, кто атакует игрока. Игроки и питомцы не задеваются. */
    public static boolean isAreaEnemy(ServerPlayer p, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e == p) return false;
        if (e instanceof Player || e instanceof ArmorStand) return false;
        if (p.isAlliedTo(e) || ownedBy(e, p)) return false;
        if (e instanceof Enemy) return true;
        return e instanceof Mob m && m.getTarget() == p;
    }

    /** Цель для навыков на одну цель: любой не союзник (игроки — только если PvP разрешено). */
    public static boolean isDirectTarget(ServerPlayer p, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e == p || e instanceof ArmorStand) return false;
        if (p.isAlliedTo(e) || ownedBy(e, p)) return false;
        if (e instanceof Player other) return p.canHarmPlayer(other);
        return true;
    }

    /** Союзники для поддержки: сам игрок, игроки без враждебной команды, свои питомцы. */
    public static boolean isAlly(ServerPlayer p, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return false;
        if (e == p) return true;
        if (e instanceof Player other) {
            if (p.getTeam() != null && other.getTeam() != null) return p.isAlliedTo(other);
            return true;
        }
        return ownedBy(e, p);
    }

    public static List<LivingEntity> enemiesAround(ServerPlayer p, Vec3 center, double r) {
        AABB box = new AABB(center, center).inflate(r);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> isAreaEnemy(p, e))) {
            if (e.distanceToSqr(center) <= r * r) out.add(e);
        }
        return out;
    }

    public static List<LivingEntity> alliesAround(ServerPlayer p, Vec3 center, double r) {
        AABB box = new AABB(center, center).inflate(r);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> isAlly(p, e))) {
            if (e.distanceToSqr(center) <= r * r) out.add(e);
        }
        if (!out.contains(p)) out.add(p);
        return out;
    }

    public static Vec3 horizontalLook(Player p) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    /** Враги в конусе перед игроком. */
    public static List<LivingEntity> enemiesInCone(ServerPlayer p, double range, double angleDeg) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double cos = Math.cos(Math.toRadians(angleDeg / 2));
        List<LivingEntity> out = new ArrayList<>();
        AABB box = p.getBoundingBox().inflate(range);
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> isDirectTarget(p, e))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range + e.getBbWidth() / 2) continue;
            if (dist < 1.0e-3 || to.normalize().dot(look) >= cos || dist < 1.2) out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        return out;
    }

    @Nullable
    public static LivingEntity closestInCone(ServerPlayer p, double range, double angleDeg) {
        List<LivingEntity> list = enemiesInCone(p, range, angleDeg);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Точка, куда смотрит игрок: блок, существо или максимальная дальность. */
    public static Vec3 lookPoint(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        HitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
                e -> e instanceof LivingEntity && e != p && !e.isSpectator(), eye.distanceToSqr(end));
        return ent != null ? ent.getLocation() : end;
    }

    @Nullable
    public static LivingEntity raycast(ServerPlayer p, double range, boolean allies) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        HitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
                e -> allies ? isAlly(p, e) && e != p : isDirectTarget(p, e), eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    /** Все враги на линии перед игроком. */
    public static List<LivingEntity> enemiesInLine(ServerPlayer p, double length, double width) {
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        Vec3 end = eye.add(dir.scale(length));
        HitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double maxLen = block.getType() != HitResult.Type.MISS ? block.getLocation().distanceTo(eye) : length;
        List<LivingEntity> out = new ArrayList<>();
        AABB box = new AABB(eye, eye.add(dir.scale(maxLen))).inflate(width);
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> isDirectTarget(p, e))) {
            Vec3 c = e.getBoundingBox().getCenter().subtract(eye);
            double along = c.dot(dir);
            if (along < 0 || along > maxLen) continue;
            double perp = c.subtract(dir.scale(along)).length();
            if (perp <= width + e.getBbWidth() / 2) out.add(e);
        }
        return out;
    }

    // ========================= Урон и эффекты =========================

    /** Базовый физический урон игрока (атрибут атаки с учётом оружия). */
    public static double melee(ServerPlayer p) {
        return Math.max(1.0, p.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    public static void hitPhysical(ServerPlayer p, LivingEntity t, double dmg) {
        applyingSkillDamage = true;
        try {
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), (float) dmg);
        } finally {
            applyingSkillDamage = false;
        }
    }

    public static void hitMagic(ServerPlayer p, LivingEntity t, double dmg) {
        applyingSkillDamage = true;
        try {
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().indirectMagic(p, p), (float) dmg);
        } finally {
            applyingSkillDamage = false;
        }
    }

    public static void heal(ServerPlayer caster, LivingEntity t, double amount) {
        PlayerData d = Hunter.get(caster);
        double mult = 1 + d.skillRank("divine_grace") * 0.10;
        float before = t.getHealth();
        t.heal((float) (amount * mult));
        float healed = t.getHealth() - before;
        if (healed > 0) {
            d.addCounter("healed", Math.round(healed));
            particles(caster.serverLevel(), net.minecraft.core.particles.ParticleTypes.HEART, t.position().add(0, t.getBbHeight() + 0.3, 0), 4, 0.3, 0.05);
        }
    }

    /** Наложить положительный эффект; Чародей (Enchanter) продлевает его. */
    public static void buff(ServerPlayer caster, LivingEntity t, Holder<MobEffect> effect, int ticks, int amplifier) {
        PlayerData d = Hunter.get(caster);
        int duration = (int) (ticks * (1 + d.skillRank("resonance") * 0.15));
        t.addEffect(new MobEffectInstance(effect, duration, amplifier), caster);
        if (t == caster) return;
        if (t instanceof Player) {
            d.addCounter("buffs_given", 1);
        } else { // pets and summons count half
            d.addCounter("buffs_pets", 1);
            if (d.counter("buffs_pets") % 2 == 0) d.addCounter("buffs_given", 1);
        }
    }

    public static void debuff(ServerPlayer caster, LivingEntity t, Holder<MobEffect> effect, int ticks, int amplifier) {
        t.addEffect(new MobEffectInstance(effect, ticks, amplifier), caster);
    }

    /** Оглушение: сильное замедление и слабость на короткое время. */
    public static void stun(ServerPlayer caster, LivingEntity t, int ticks) {
        debuff(caster, t, MobEffects.MOVEMENT_SLOWDOWN, ticks, 4);
        debuff(caster, t, MobEffects.WEAKNESS, ticks, 1);
        if (t instanceof Mob m) m.getNavigation().stop();
    }

    public static void knockback(LivingEntity t, Vec3 from, double strength) {
        Vec3 d = from.subtract(t.position());
        t.knockback(strength, d.x, d.z);
        t.hurtMarked = true;
    }

    public static void launch(LivingEntity t, double up) {
        t.setDeltaMovement(t.getDeltaMovement().add(0, up, 0));
        t.hurtMarked = true;
    }

    /** Рывок игрока. Сервер задаёт скорость, клиент получает её через hurtMarked. */
    public static void dash(ServerPlayer p, Vec3 dir, double power, double up) {
        Vec3 v = dir.normalize().scale(power);
        p.setDeltaMovement(v.x, Math.max(p.getDeltaMovement().y, 0) + up, v.z);
        p.hurtMarked = true;
        p.fallDistance = 0;
    }

    /** Кадры неуязвимости (уклонение во время рывка). */
    public static void iframes(SkillContext c, int ticks) {
        state(c.data(), c.level(), "iframe", ticks);
    }

    public static void state(PlayerData d, ServerLevel level, String key, int ticks) {
        d.timedStates.put(key, level.getGameTime() + ticks);
        d.markDirty();
    }

    /** Метка на цели (Метка охотника, Сокрушение, Порча): хранится в persistent data сущности. */
    public static void mark(LivingEntity target, String key, ServerPlayer owner, int ticks) {
        target.getPersistentData().putLong("solostatus_" + key + "_until", target.level().getGameTime() + ticks);
        target.getPersistentData().putUUID("solostatus_" + key + "_by", owner.getUUID());
    }

    public static boolean marked(LivingEntity target, String key, Player by) {
        var tag = target.getPersistentData();
        String until = "solostatus_" + key + "_until";
        if (!tag.contains(until) || tag.getLong(until) < target.level().getGameTime()) return false;
        return by == null || (tag.hasUUID("solostatus_" + key + "_by") && tag.getUUID("solostatus_" + key + "_by").equals(by.getUUID()));
    }

    // ========================= Стрелы =========================

    public static Arrow arrow(ServerPlayer p, float yawOffset, float pitchOffset, float velocity, double damage, boolean crit) {
        Arrow arrow = new Arrow(p.level(), p, new ItemStack(Items.ARROW), null);
        arrow.shootFromRotation(p, p.getXRot() + pitchOffset, p.getYRot() + yawOffset, 0.0f, velocity, 0.5f);
        arrow.setBaseDamage(damage / Math.max(0.1, velocity));
        arrow.setCritArrow(crit);
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        p.level().addFreshEntity(arrow);
        return arrow;
    }

    /** Стрела, падающая с неба в точку (Дождь стрел). */
    public static void skyArrow(ServerPlayer p, Vec3 at, double damage) {
        Arrow arrow = new Arrow(p.level(), p, new ItemStack(Items.ARROW), null);
        arrow.setPos(at.x, at.y + 12, at.z);
        arrow.shoot(0, -1, 0, 2.2f, 1.5f);
        arrow.setBaseDamage(damage / 2.2);
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        p.level().addFreshEntity(arrow);
    }

    // ========================= Визуал =========================

    /** Ванильный взмах — только если навык не проиграл анимацию Epic Fight в этот тик. */
    public static void swing(ServerPlayer p) {
        PlayerData d = Hunter.get(p);
        long now = p.serverLevel().getGameTime();
        if (d.animatedTick == now) return;
        // the skill really acts: now its Epic Fight animation may play (never for a cast that found no target)
        if (d.castingSkill != null && com.vladosiwe.solostatus.compat.CompatManager.playSkillAnimation(p, d.castingSkill)) {
            d.animatedTick = now;
            return;
        }
        p.swing(InteractionHand.MAIN_HAND, true);
    }

    public static void particles(ServerLevel level, ParticleOptions type, Vec3 pos, int count, double spread, double speed) {
        level.sendParticles(type, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    public static void ring(ServerLevel level, ParticleOptions type, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(type, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0, 0.05, 0, 0.0);
        }
    }

    public static void line(ServerLevel level, ParticleOptions type, Vec3 a, Vec3 b, double step) {
        Vec3 d = b.subtract(a);
        int n = Math.max(1, (int) (d.length() / step));
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(d.scale(i / (double) n));
            level.sendParticles(type, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static boolean isSpec(SkillContext c, Specialization s) {
        return c.data().specialization == s;
    }
}
