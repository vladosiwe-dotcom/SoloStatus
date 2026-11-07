package com.vladosiwe.solostatus.data;

import com.vladosiwe.solostatus.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;

/** Короткий доступ к профилю охотника на сервере. */
public final class Hunter {
    private Hunter() {}

    public static PlayerData get(Player player) {
        return player.getData(ModAttachments.PLAYER_DATA);
    }
}
