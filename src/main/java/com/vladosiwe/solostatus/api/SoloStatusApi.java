package com.vladosiwe.solostatus.api;

import com.vladosiwe.solostatus.achievement.TitleSystem;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.level.LevelSystem;
import com.vladosiwe.solostatus.level.XpReason;
import net.minecraft.server.level.ServerPlayer;

/**
 * Открытый API Системы для других модов (например, Lord's Outpost). Только сервер.
 *
 * Другие моды вызывают его через отражение, без жёсткой зависимости: имена и сигнатуры методов
 * здесь — договор, менять их нельзя.
 */
public final class SoloStatusApi {
    /** Счётчик «сейчас игрок — Лорд аванпоста» (0 или 1). От него зависит титул «Лорд». */
    public static final String LORD_COUNTER = "outpost_lord";

    private SoloStatusApi() {}

    /** Прибавить к счётчику (достижения проверяются автоматически). */
    public static void addCounter(ServerPlayer player, String key, long amount) {
        if (key == null || key.isEmpty() || amount == 0) return;
        Hunter.get(player).addCounter(key, amount);
    }

    /** Установить счётчик. Для {@link #LORD_COUNTER} заодно надевает или снимает титул «Лорд». */
    public static void setCounter(ServerPlayer player, String key, long value) {
        if (key == null || key.isEmpty()) return;
        PlayerData d = Hunter.get(player);
        long old = d.counter(key);
        if (old == value) return;
        d.addCounter(key, value - old);
        if (LORD_COUNTER.equals(key)) TitleSystem.onLordship(player, value > 0);
    }

    /** Опыт Системы за дела в других модах (штурм, задания). Ничего не делает, пока Система не пробуждена. */
    public static void grantXp(ServerPlayer player, long amount) {
        LevelSystem.grantXp(player, amount, XpReason.OUTPOST);
    }
}
