package com.vladosiwe.solostatus.registry;

import com.vladosiwe.solostatus.data.History;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.network.Notices;
import com.vladosiwe.solostatus.skill.Scheduler;
import com.vladosiwe.solostatus.skill.SkillFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * How legendary weapons are found and what they do besides their skill.
 *  - A boss defeated by a hunter can give the legendary weapon of that hunter's class: 50% on the first victory over
 *    a kind of boss, 15% after; only while the hunter does not already carry it (no hoarding copies). It goes straight
 *    into the inventory, so a dragon over the void does not lose it.
 *  - Mountain Cleaver: +30% against an enemy still at full health (the opening blow). Dusk Fang: +20% from behind and
 *    every blow slows. Dawn Scepter: +50% against the undead (and +20% healing, in SkillFx.heal). Last Bastion:
 *    returns a quarter of the damage it blocks. Starwell Staff and the bow have their numbers on the items.
 */
public final class LegendaryDrops {
    public static final double FIRST_WIN_CHANCE = 0.5, REPEAT_CHANCE = 0.15;

    private LegendaryDrops() {}

    public static void onBossKill(ServerPlayer p, PlayerData d, boolean firstWin) {
        var kind = LegendaryWeapons.forClass(d.hunterClass);
        if (kind == null || carries(p, kind)) return;
        if (p.getRandom().nextDouble() >= (firstWin ? FIRST_WIN_CHANCE : REPEAT_CHANCE)) return;
        give(p, d, kind);
    }

    /** Hands the weapon over with the System's announcement. */
    public static void give(ServerPlayer p, PlayerData d, LegendaryWeapons.Kind kind) {
        ItemStack stack = new ItemStack(kind.item());
        if (!p.getInventory().add(stack)) p.drop(stack, false);
        d.addCounter("legendary_found", 1);
        History.add(p, d, "legendary", kind.key, 0);
        SkillFx.particles(p.serverLevel(), ParticleTypes.TOTEM_OF_UNDYING, p.position().add(0, 1, 0), 60, 0.6, 0.4);
        SkillFx.particles(p.serverLevel(), ParticleTypes.END_ROD, p.position().add(0, 1, 0), 40, 0.4, 0.15);
        SkillFx.sound(p.serverLevel(), p.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Notices.send(p, Notices.ACHIEVEMENT, Component.translatable("solostatus.notice.legendary_weapon"),
                Component.translatable("item.solostatus." + kind.key), kind.item());
    }

    /** In the inventory, the hands or the ender chest. */
    public static boolean carries(ServerPlayer p, LegendaryWeapons.Kind kind) {
        if (p.getInventory().hasAnyMatching(s -> LegendaryWeapons.of(s) == kind)) return true;
        var ender = p.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) if (LegendaryWeapons.of(ender.getItem(i)) == kind) return true;
        return false;
    }

    /** Extra damage share of a blow made with a legendary weapon in the main hand. */
    public static double offense(ServerPlayer p, LivingEntity target, boolean melee, boolean behind) {
        var kind = LegendaryWeapons.of(p.getMainHandItem());
        if (kind == null || !melee) return 0;
        return switch (kind) {
            case MOUNTAIN_CLEAVER -> target.getHealth() >= target.getMaxHealth() * 0.99f ? 0.30 : 0;
            case DUSK_FANG -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0), p);
                yield behind ? 0.20 : 0;
            }
            case DAWN_SCEPTER -> target.getType().is(EntityTypeTags.UNDEAD) ? 0.5 : 0;
            default -> 0;
        };
    }

    /** Last Bastion returns a quarter of what it blocks. */
    public static void onBlock(ServerPlayer p, LivingEntity attacker, float blocked) {
        if (attacker == null || blocked <= 0 || !(LegendaryWeapons.of(p.getUseItem()) == LegendaryWeapons.Kind.LAST_BASTION)) return;
        float back = blocked * 0.25f;
        Scheduler.later(1, () -> {
            if (attacker.isAlive()) attacker.hurt(p.damageSources().thorns(p), back);
        });
        SkillFx.particles(p.serverLevel(), SkillFx.HOLY_DUST, p.getEyePosition().add(p.getLookAngle()), 10, 0.2, 0.05);
    }
}
