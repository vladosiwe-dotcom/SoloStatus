package com.vladosiwe.solostatus.mastery;

import com.vladosiwe.solostatus.combat.Threat;
import com.vladosiwe.solostatus.combat.WeaponType;
import com.vladosiwe.solostatus.compat.ironsspells.IronsSpellsBridge;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.skill.Scheduler;
import com.vladosiwe.solostatus.skill.SkillFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;

/**
 * What weapon mastery gives at its high milestones.
 *  - 40: the weapon's technique, a property of every blow with it (here, read by CombatEvents);
 *  - 50: the weapon's awakened skill develops (read in that skill's own effect through {@link #mastered}).
 */
public final class MasteryTechniques {
    public static final int TECHNIQUE = 40;
    public static final int MASTERED = 50;

    private MasteryTechniques() {}

    public static int level(PlayerData d, WeaponType type) {
        return type == null ? 0 : MasteryMath.level(d.masteryXp(type.key));
    }

    /** Mastery 50 with this weapon: its awakened skill is developed. */
    public static boolean mastered(PlayerData d, WeaponType type) {
        return level(d, type) >= MASTERED;
    }

    private static boolean technique(PlayerData d, WeaponType type) {
        return level(d, type) >= TECHNIQUE;
    }

    /** Extra damage share of a blow (added to the one sum in CombatEvents.handleOffense). */
    public static double offense(ServerPlayer p, PlayerData d, LivingEntity target, WeaponType type, Entity direct, double dist) {
        if (type == null || !technique(d, type)) return 0;
        return switch (type) {
            case DAGGER -> target.getHealth() < target.getMaxHealth() * 0.3f ? 0.25 : 0;      // finishing cut
            case AXE -> target.getAttributeValue(Attributes.ARMOR) >= 8 ? 0.15 : 0;           // splits armour
            case SPEAR -> dist >= 3 ? 0.20 : 0;                                                 // the spear's reach
            case BOW -> direct instanceof AbstractArrow a && a.isCritArrow() ? 0.15 : 0;        // a full draw
            case CROSSBOW -> target instanceof Mob m && m.getTarget() != p ? 0.25 : 0;          // a bolt from nowhere
            default -> 0;
        };
    }

    /** Sword technique: every third blow of a sword series is a sure crit. */
    public static boolean forceCrit(PlayerData d, WeaponType type, boolean series) {
        return type == WeaponType.SWORD && series && technique(d, type) && d.comboCount > 0 && d.comboCount % 3 == 0;
    }

    /** After a blow landed for {@code amount}. */
    public static void afterHit(ServerPlayer p, PlayerData d, LivingEntity target, WeaponType type, float amount, boolean skill, long now) {
        if (type == null || !technique(d, type)) return;
        switch (type) {
            case GREATSWORD -> { // a wide swing catches one more enemy
                if (skill) return;
                for (LivingEntity e : SkillFx.enemiesAround(p, target.position(), 2.5)) {
                    if (e == target) continue;
                    double dmg = amount * 0.3;
                    Scheduler.later(1, () -> { if (e.isAlive()) SkillFx.hitPhysical(p, e, dmg); });
                    SkillFx.particles(p.serverLevel(), ParticleTypes.SWEEP_ATTACK, e.getBoundingBox().getCenter(), 1, 0, 0);
                    break;
                }
            }
            case MACE -> { // a heavy blow can daze
                if (!skill && !Threat.isBoss(target) && p.getRandom().nextDouble() < 0.08) SkillFx.stun(p, target, 20);
            }
            case STAFF -> { // magic through a staff gives some mana back
                if (!d.stateActive("staff_flow_cd", now)) {
                    SkillFx.state(d, p.serverLevel(), "staff_flow_cd", 10);
                    IronsSpellsBridge.spendMana(p, -2f);
                }
            }
            default -> { }
        }
    }

    /** Shield technique: a counted block throws the attacker back. */
    public static void onBlock(ServerPlayer p, PlayerData d, LivingEntity attacker) {
        if (attacker == null || !technique(d, WeaponType.SHIELD)) return;
        SkillFx.knockback(attacker, p.position(), 0.6);
        SkillFx.sound(p.serverLevel(), p.position(), SoundEvents.SHIELD_BLOCK, 0.6f, 0.7f);
    }
}
