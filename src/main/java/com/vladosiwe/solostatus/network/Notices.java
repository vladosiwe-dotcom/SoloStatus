package com.vladosiwe.solostatus.network;

import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Отправка системных уведомлений и синхронизация профиля. */
public final class Notices {
    public static final int INFO = 0, LEVEL_UP = 1, AWAKENING = 2, ACHIEVEMENT = 3, HIDDEN_ACHIEVEMENT = 4,
            RANK_UP = 5, WARNING = 6, CLASS = 7;

    private Notices() {}

    public static void send(ServerPlayer player, int kind, Component title, Component body) {
        PacketDistributor.sendToPlayer(player, new Payloads.Notice(kind, title, body));
    }

    public static void warn(ServerPlayer player, String key, Object... args) {
        send(player, WARNING, Component.translatable("solostatus.notice.warning"), Component.translatable(key, args));
    }

    /** Отправляет профиль, если он изменился. Вызывается раз в тик игрока — изменения за тик объединяются. */
    public static void flush(ServerPlayer player) {
        PlayerData d = Hunter.get(player);
        if (!d.dirty) return;
        d.dirty = false;
        PacketDistributor.sendToPlayer(player, new Payloads.SyncData(d.write()));
    }

    public static void forceSync(ServerPlayer player) {
        Hunter.get(player).dirty = true;
        flush(player);
    }

    public static void syncEnergy(ServerPlayer player, PlayerData d) {
        PacketDistributor.sendToPlayer(player, new Payloads.EnergySync(d.energy));
    }

    public static float maxEnergy(PlayerData d) {
        return (float) StatFormulas.maxEnergy(d);
    }
}
