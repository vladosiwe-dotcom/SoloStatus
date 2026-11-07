package com.vladosiwe.solostatus.data;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.achievement.AchievementSystem;
import com.vladosiwe.solostatus.command.SoloCommands;
import com.vladosiwe.solostatus.network.Notices;
import com.vladosiwe.solostatus.quality.QualitySystem;
import com.vladosiwe.solostatus.rank.HunterRankSystem;
import com.vladosiwe.solostatus.skill.Scheduler;
import com.vladosiwe.solostatus.skill.SkillFx;
import com.vladosiwe.solostatus.skill.SkillUnlockSystem;
import com.vladosiwe.solostatus.stats.AttributeSystem;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Жизненный цикл профиля: вход, возрождение, смена измерения, периодические задачи.
 * Периодические задачи выполняются с прореживанием (раз в 20/40/100 тиков), никаких тяжёлых проверок каждый тик.
 */
@EventBusSubscriber(modid = SoloStatus.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            AttributeSystem.apply(p);
            Hunter.get(p).markDirty();
            Notices.forceSync(p);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            PlayerData d = Hunter.get(p);
            AttributeSystem.apply(p);
            p.setHealth(p.getMaxHealth());
            d.timedStates.clear();
            d.energy = (float) StatFormulas.maxEnergy(d);
            Notices.forceSync(p);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            AttributeSystem.apply(p);
            Notices.forceSync(p);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        PlayerData d = Hunter.get(p);
        int tick = p.tickCount;

        if (tick % 20 == 0 && d.softDirty) {
            d.softDirty = false;
            d.dirty = true;
        }
        if (tick % 20 == 0 && d.hasClass()) {
            // Восстановление энергии
            float max = (float) StatFormulas.maxEnergy(d);
            if (d.energy < max) {
                d.energy = Math.min(max, d.energy + (float) StatFormulas.energyRegen(d));
                Notices.syncEnergy(p, d);
            }
            // Пробуждение навыков, достижения, ранг — только если что-то изменилось
            if (d.unlockCheckPending) {
                SkillUnlockSystem.check(p);
                AchievementSystem.check(p, d);
                HunterRankSystem.update(p, d);
            }
            // Очистка истёкших состояний навыков
            long now = p.serverLevel().getGameTime();
            if (d.timedStates.values().removeIf(end -> end < now - 20 * 60 * 10)) d.dirty = true;
        }
        if (tick % 40 == 0) QualitySystem.tickHeld(p);
        if (tick % 100 == 0 && d.hasClass()) {
            // Дистанция бега из ванильной статистики (метры) — для навыка «Спринт» и достижения
            long meters = p.getStats().getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM)) / 100;
            if (meters != d.counter("sprint_m")) {
                d.counters.put("sprint_m", meters);
                d.unlockCheckPending = true;
            }
            // Аура бодрости Поддержки
            int vigor = d.skillRank("aura_of_vigor");
            if (vigor > 0) {
                for (LivingEntity a : SkillFx.alliesAround(p, p.position(), 8)) {
                    if (a.getHealth() < a.getMaxHealth()) SkillFx.heal(p, a, 0.5 * vigor);
                }
            }
        }
        Notices.flush(p);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Scheduler.tick();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Scheduler.clear();
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        SoloCommands.register(event.getDispatcher());
    }
}
