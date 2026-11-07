package com.vladosiwe.solostatus.client;

import com.vladosiwe.solostatus.data.PlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Клиентский кэш данных, полученных от сервера. Клиент никогда не изменяет их сам. */
public final class ClientState {
    public static PlayerData data = new PlayerData();
    public static boolean synced = false;

    /** Активные уведомления на экране. */
    public static final List<Notice> NOTICES = new ArrayList<>();
    private static final Deque<Notice> QUEUE = new ArrayDeque<>();

    @Nullable public static CompoundTag inspectData;
    public static int inspectEntityId = -1;
    public static boolean openStatusNextTick = false;

    private ClientState() {}

    public record Notice(int kind, Component title, Component body, long start, long duration) {
        public float progress(long now) {
            return (now - start) / (float) duration;
        }
    }

    public static void addNotice(int kind, Component title, Component body) {
        long duration = kind == 2 || kind == 4 || kind == 5 || kind == 7 ? 5500 : 3500;
        QUEUE.add(new Notice(kind, title, body, 0, duration));
    }

    /** Переносит уведомления из очереди на экран (не более 3 одновременно). */
    public static void pumpNotices(long now) {
        NOTICES.removeIf(n -> n.progress(now) >= 1f);
        while (NOTICES.size() < 3 && !QUEUE.isEmpty()) {
            Notice n = QUEUE.poll();
            NOTICES.add(new Notice(n.kind(), n.title(), n.body(), now, n.duration()));
        }
    }

    public static void reset() {
        data = new PlayerData();
        synced = false;
        NOTICES.clear();
        QUEUE.clear();
        inspectData = null;
        inspectEntityId = -1;
    }
}
