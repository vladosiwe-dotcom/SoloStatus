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
    private TitleSystem() {}

    public static void equip(ServerPlayer player, String id) {
        PlayerData d = Hunter.get(player);
        if (!id.isEmpty()) {
            AchievementDef def = Achievements.get(id);
            if (def == null || !def.title() || !d.achievements.contains(id)) return; // защита от подмены пакета
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
