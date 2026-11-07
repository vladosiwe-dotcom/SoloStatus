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

    /** Всплывающие подписи «+N опыта» (одинаковые источники за 1.2 с объединяются). */
    public static final List<XpPopup> XP_POPUPS = new ArrayList<>();

    public static final class XpPopup {
        public final int reason;
        public long amount;
        public long start;

        XpPopup(int reason, long amount, long start) {
            this.reason = reason;
            this.amount = amount;
            this.start = start;
        }
    }

    public static void addXpPopup(int reason, int amount) {
        long now = System.currentTimeMillis();
        for (XpPopup p : XP_POPUPS) {
            if (p.reason == reason && now - p.start < 1200) {
                p.amount += amount;
                p.start = now;
                return;
            }
        }
        XP_POPUPS.add(new XpPopup(reason, amount, now));
        while (XP_POPUPS.size() > 4) XP_POPUPS.remove(0);
    }

    @Nullable public static CompoundTag inspectData;
    public static int inspectEntityId = -1;
    public static boolean openStatusNextTick = false;

    private ClientState() {}

    public record Notice(int kind, Component title, Component body, net.minecraft.world.item.ItemStack icon, long start, long duration) {
        public float progress(long now) {
            return (now - start) / (float) duration;
        }
    }

    public static void addNotice(int kind, Component title, Component body, net.minecraft.world.item.ItemStack icon) {
        long duration = kind == 2 || kind == 3 || kind == 4 || kind == 5 || kind == 7 ? 5500 : 3500;
        QUEUE.add(new Notice(kind, title, body, icon, 0, duration));
    }

    /** Переносит уведомления из очереди на экран (не более 3 одновременно). */
    public static void pumpNotices(long now) {
        NOTICES.removeIf(n -> n.progress(now) >= 1f);
        while (NOTICES.size() < 3 && !QUEUE.isEmpty()) {
            Notice n = QUEUE.poll();
            NOTICES.add(new Notice(n.kind(), n.title(), n.body(), n.icon(), now, n.duration()));
        }
    }

    public static void reset() {
        data = new PlayerData();
        synced = false;
        NOTICES.clear();
        QUEUE.clear();
        XP_POPUPS.clear();
        inspectData = null;
        inspectEntityId = -1;
    }
}
