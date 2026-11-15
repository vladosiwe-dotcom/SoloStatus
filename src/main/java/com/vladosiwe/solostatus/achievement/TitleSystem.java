package com.vladosiwe.solostatus.achievement;

import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.network.Notices;
import com.vladosiwe.solostatus.network.SystemPresence;
import com.vladosiwe.solostatus.stats.AttributeSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Титулы: открываются трудными достижениями, активен только один. Эффект действует, пока титул надет,
 * поэтому выбор титула — осознанное решение под стиль игры, а не незаметное суммирование бонусов.
 */
public final class TitleSystem {
    /** Титул владельца аванпоста (мод Lord's Outpost). */
    public static final String LORD = "lord";

    private TitleSystem() {}

    /**
     * Игрок стал Лордом или перестал им быть. Став Лордом, он сразу получает и надевает титул;
     * лишившись владения, теряет право его носить.
     */
    public static void onLordship(ServerPlayer player, boolean lord) {
        PlayerData d = Hunter.get(player);
        if (lord) {
            AchievementDef def = Achievements.get(LORD);
            if (def != null && !d.achievements.contains(LORD)) AchievementSystem.grant(player, d, def);
            equip(player, LORD);
        } else if (d.titleIs(LORD)) {
            equip(player, "");
        }
    }

    public static void equip(ServerPlayer player, String id) {
        PlayerData d = Hunter.get(player);
        if (!id.isEmpty()) {
            AchievementDef def = Achievements.get(id);
            if (def == null || !def.title() || !d.achievements.contains(id)) return; // защита от подмены пакета
            // Титул «Лорд» носят только, пока владеют аванпостом.
            if (LORD.equals(id) && d.counter(com.vladosiwe.solostatus.api.SoloStatusApi.LORD_COUNTER) <= 0) {
                Notices.send(player, Notices.WARNING, Component.translatable("solostatus.notice.lord_lost"), Component.translatable("solostatus.notice.lord_lost.desc"));
                return;
            }
        }
        if (d.title.equals(id)) return;
        d.title = id;
        AttributeSystem.apply(player);
        d.markDirty();
        SystemPresence.refresh(player);
        if (!id.isEmpty()) {
            AchievementDef def = Achievements.get(id);
            Notices.send(player, Notices.CLASS, Component.translatable("solostatus.notice.title", def.name()), def.titleEffect(), def.icon());
        }
    }
}
