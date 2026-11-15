package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.combat.Threat;
import com.vladosiwe.solostatus.combat.WeaponType;
import com.vladosiwe.solostatus.compat.ironsspells.IronsSpellsBridge;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The later steps of every specialization, read by the combat code. A specialization opens its main skill at 10 and
 * a passive at 15; at 30 a passive that develops the main skill (most of those live in that skill's own effect, in
 * SkillEffects), at 60 a passive of the mastered style. The ones that react to hits, blocks and kills are here.
 * All of them are ranked passives (1..3), so a rank is always read with PlayerData.skillRank.
 */
public final class StyleSkills {
    private StyleSkills() {}

    /** Extra damage share for a hit by the hunter (added to the one sum in CombatEvents.handleOffense). */
    public static double offense(ServerPlayer p, PlayerData d, LivingEntity target, boolean melee, boolean projectile,
                                 boolean behind, Entity direct, WeaponType type, long now) {
        double add = 0;
        int r;
        if ((r = d.skillRank("undying_rage")) > 0 && p.getHealth() < p.getMaxHealth() * 0.3f) add += 0.06 * r;
        if ((r = d.skillRank("thick_of_battle")) > 0 && inThickOfBattle(p)) add += 0.04 * r;
        if ((r = d.skillRank("night_blade")) > 0 && melee && behind) add += 0.08 * r;
        if (projectile && direct != null && target.getHealth() < target.getMaxHealth() * 0.5f) {
            add += 0.25 * direct.getPersistentData().getInt("solostatus_killshot"); // Sniper Shot with Killshot
        }
        if ((r = d.skillRank("wind_runner")) > 0 && projectile && d.stateActive("wind_runner", now)) add += 0.10 * r;
        if ((r = d.skillRank("deep_curse")) > 0 && SkillFx.marked(target, "hex", null)) add += 0.05 * r;
        if ((r = d.skillRank("heavy_bolts")) > 0 && projectile && type == WeaponType.CROSSBOW) add += 0.05 * r;
        if ((r = d.skillRank("song_of_valor")) > 0 && d.stateActive("battle_hymn", now)) add += 0.05 * r;
        return add;
    }

    /** Extra crit chance for a hit by the hunter. */
    public static double critChance(PlayerData d, boolean projectile) {
        return projectile ? 0.03 * d.skillRank("eagle_eye") : 0;
    }

    /** Multiplier of the damage the hunter takes. */
    public static double defense(ServerPlayer p, PlayerData d, Entity attacker, long now) {
        double mult = 1;
        int r;
        if ((r = d.skillRank("undying_rage")) > 0 && p.getHealth() < p.getMaxHealth() * 0.3f) mult *= 1 - 0.06 * r;
        if ((r = d.skillRank("thick_of_battle")) > 0 && inThickOfBattle(p)) mult *= 1 - 0.04 * r;
        if ((r = d.skillRank("deep_curse")) > 0 && attacker instanceof LivingEntity le && SkillFx.marked(le, "hex", null)) mult *= 1 - 0.05 * r;
        if ((r = d.skillRank("song_of_valor")) > 0 && d.stateActive("battle_hymn", now)) mult *= 1 - 0.05 * r;
        return mult;
    }

    /** After the hunter took {@code taken} from a hit (already reduced). */
    public static void afterHit(ServerPlayer p, PlayerData d, Entity attacker, boolean meleeHit, float taken, long now) {
        int r;
        if ((r = d.skillRank("mirror_aegis")) > 0 && meleeHit && attacker instanceof LivingEntity le && d.stateActive("mirror_aegis", now)) {
            float reflect = taken * 0.15f * r;
            Scheduler.later(1, () -> {
                if (le.isAlive()) le.hurt(p.damageSources().thorns(p), reflect);
            });
        }
        if ((r = d.skillRank("seraph_ward")) > 0 && taken > 0 && !d.stateActive("seraph_ward_cd", now)
                && p.getHealth() - taken < p.getMaxHealth() * 0.25f && p.getHealth() - taken > 0) {
            SkillFx.state(d, p.serverLevel(), "seraph_ward_cd", 20 * (120 - 20 * r));
            p.heal(p.getMaxHealth() * (0.20f + 0.05f * r));
            SkillFx.particles(p.serverLevel(), SkillFx.HOLY_DUST, p.position().add(0, 1.5, 0), 40, 0.6, 0.05);
            SkillFx.sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        }
    }

    /** A shield block that counted. */
    public static void onBlock(ServerPlayer p, PlayerData d, long now) {
        int r = d.skillRank("stalwart");
        if (r <= 0 || d.stateActive("stalwart_cd", now)) return;
        SkillFx.state(d, p.serverLevel(), "stalwart_cd", 20);
        d.energy = (float) Math.min(StatFormulas.maxEnergy(d), d.energy + 3 * r);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 0, false, false));
    }

    /** Every fourth blow of a series (mastery 25+). */
    public static void onCombo(PlayerData d, long now) {
        int r = d.skillRank("endless_dance");
        if (r > 0) shorten(d, "blade_storm", 20L * r, now);
    }

    /** A melee hit landed during Arcane Strike: runes burst around the target. */
    public static void onArcaneStrikeHit(ServerPlayer p, PlayerData d, LivingEntity target, float amount) {
        int r = d.skillRank("runic_edge");
        if (r <= 0) return;
        double dmg = amount * 0.2 * r;
        Scheduler.later(1, () -> {
            if (!p.isAlive()) return;
            for (LivingEntity e : SkillFx.enemiesAround(p, target.position(), 2.5)) if (e != target) SkillFx.hitMagic(p, e, dmg);
            SkillFx.ring(p.serverLevel(), ParticleTypes.WITCH, target.position().add(0, 0.5, 0), 2.5, 16);
        });
    }

    /** A counted kill by the hunter. */
    public static void onKill(ServerPlayer p, PlayerData d, LivingEntity dead, boolean melee, long now) {
        ServerLevel level = p.serverLevel();
        int r;
        if ((r = d.skillRank("bloodbath")) > 0 && d.stateActive("berserk", now)) {
            d.timedStates.merge("berserk", 20L * r, Long::sum);
            p.heal(p.getMaxHealth() * 0.04f * r);
            SkillFx.particles(level, SkillFx.BLOOD_DUST, p.position().add(0, 1, 0), 20, 0.4, 0.05);
        }
        if ((r = d.skillRank("spreading_hex")) > 0 && SkillFx.marked(dead, "hex", p)) {
            LivingEntity next = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : SkillFx.enemiesAround(p, dead.position(), 6 + 2 * r)) {
                double dist = e.distanceToSqr(dead);
                if (e != dead && !SkillFx.marked(e, "hex", null) && dist < best) {
                    best = dist;
                    next = e;
                }
            }
            if (next != null) {
                SkillFx.mark(next, "hex", p, 160);
                SkillFx.debuff(p, next, MobEffects.WEAKNESS, 160, 0);
                SkillFx.debuff(p, next, MobEffects.MOVEMENT_SLOWDOWN, 160, 1);
                SkillFx.debuff(p, next, MobEffects.GLOWING, 160, 0);
                SkillFx.line(level, ParticleTypes.WITCH, dead.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), 0.4);
            }
        }
        if ((r = d.skillRank("decisive_duel")) > 0 && SkillFx.marked(dead, "duel", p)) {
            d.cooldowns.remove("duel_challenge");
            p.heal(p.getMaxHealth() * 0.08f * r);
            SkillFx.sound(level, p.position(), SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
        }
        if ((r = d.skillRank("toxic_burst")) > 0 && dead.hasEffect(MobEffects.POISON)) {
            for (LivingEntity e : SkillFx.enemiesAround(p, dead.position(), 3)) {
                if (e != dead) SkillFx.debuff(p, e, MobEffects.POISON, 40 + 20 * r, 0);
            }
            SkillFx.particles(level, ParticleTypes.ITEM_SLIME, dead.position().add(0, 0.5, 0), 30, 1.2, 0.05);
        }
        if ((r = d.skillRank("mana_conduit")) > 0 && melee) IronsSpellsBridge.spendMana(p, -5f * r);
        d.markDirty();
    }

    /** Vengeance with Eye for an Eye: share of the taken damage stored, and its cap. */
    public static double vengeanceShare(PlayerData d, int vengeance) {
        return 0.25 + 0.05 * (vengeance - 1) + 0.10 * d.skillRank("eye_for_eye");
    }

    public static double vengeanceCap(PlayerData d, int vengeance) {
        return 20 + 6 * vengeance + 10 * d.skillRank("eye_for_eye");
    }

    private static boolean inThickOfBattle(ServerPlayer p) {
        int n = 0;
        for (LivingEntity e : SkillFx.enemiesAround(p, p.position(), 6)) if (Threat.fairness(e, p) > 0 && ++n >= 3) return true;
        return false;
    }

    private static void shorten(PlayerData d, String skill, long ticks, long now) {
        Long ready = d.cooldowns.get(skill);
        if (ready == null || ready <= now) return;
        d.cooldowns.put(skill, Math.max(now, ready - ticks));
        d.markDirty();
    }
}
