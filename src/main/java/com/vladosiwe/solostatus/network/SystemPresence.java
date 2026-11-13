package com.vladosiwe.solostatus.network;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * «Присутствие Системы» в мультиплеере: когда охотник открывает окно Системы, игроки рядом видят
 * голографическое окно перед ним с публичными данными (имя, уровень, ранг, титул, класс, характеристики).
 * Отправляются только данные, разрешённые правилами осмотра; скрытый профиль — без характеристик.
 * Рассылка только игрокам, которые отслеживают этого игрока (в зоне видимости), а не всему серверу.
 */
@EventBusSubscriber(modid = SoloStatus.MODID)
public final class SystemPresence {
    private static final Set<UUID> OPEN = new HashSet<>();

    private SystemPresence() {}

    public static void setOpen(ServerPlayer p, boolean open) {
        boolean changed = open ? OPEN.add(p.getUUID()) : OPEN.remove(p.getUUID());
        if (changed) broadcast(p, open);
    }

    /** Обновить содержимое окна (после изменения характеристик, титула и т.п.). */
    public static void refresh(ServerPlayer p) {
        if (OPEN.contains(p.getUUID())) broadcast(p, true);
    }

    private static void broadcast(ServerPlayer p, boolean open) {
        PacketDistributor.sendToPlayersTrackingEntity(p, new Payloads.Presence(p.getId(), open, open ? publicInfo(p) : new CompoundTag()));
    }

    public static CompoundTag publicInfo(ServerPlayer p) {
        PlayerData d = Hunter.get(p);
        CompoundTag t = new CompoundTag();
        t.putString("name", p.getDisplayName().getString());
        t.putInt("level", d.level);
        t.putInt("rank", d.rank.ordinal());
        t.putString("title", d.title);
        t.putBoolean("private", d.privateProfile);
        if (d.hunterClass != null) t.putString("class", d.hunterClass.key);
        if (d.specialization != null) t.putString("spec", d.specialization.key);
        t.putBoolean("advanced", d.advanced);
        if (!d.privateProfile) t.putIntArray("stats", d.stats.clone());
        return t;
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer
                && OPEN.contains(target.getUUID())) {
            ModNetwork.toPlayer(viewer, new Payloads.Presence(target.getId(), true, publicInfo(target)));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) setOpen(p, false);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) setOpen(p, false);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer p) setOpen(p, false);
    }
}
