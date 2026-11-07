package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.data.PlayerData;
import net.minecraft.network.chat.Component;

/** Условие открытия/улучшения навыка. Прогресс считается и на сервере, и на клиенте для отображения. */
public interface Requirement {
    /** Прогресс выполнения 0..1. */
    double progress(PlayerData d);

    /** Текст условия для интерфейса. */
    Component describe(PlayerData d);

    default boolean met(PlayerData d) {
        return progress(d) >= 1.0;
    }
}
