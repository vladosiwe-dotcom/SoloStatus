package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.compat.CompatManager;
import com.vladosiwe.solostatus.compat.ironsspells.IronsSpellsBridge;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The resource a hunter spends on skills. Fighters use the System's stamina. Casters (Mage, Support) use Iron's
 * Spells mana: the same bar their spells drain, so there is one mana, not two. Without Iron's (it is required, but
 * a broken API is handled) casters fall back to the System's own mana.
 */
public final class ManaPool {
    private ManaPool() {}

    public static boolean ironsMana(PlayerData d) {
        return StatFormulas.usesMana(d) && d.hunterClass != null && CompatManager.ironsLoaded();
    }

    /** Current amount on the server. */
    public static float current(ServerPlayer player, PlayerData d) {
        if (ironsMana(d)) {
            float m = IronsSpellsBridge.mana(player);
            if (m >= 0) return m;
        }
        return d.energy;
    }

    /** Current amount for the local player's HUD and window. */
    public static float clientCurrent(PlayerData d) {
        if (ironsMana(d)) {
            float m = IronsSpellsBridge.clientMana();
            if (m >= 0) return m;
        }
        return d.energy;
    }

    public static float max(Player player, PlayerData d) {
        if (ironsMana(d)) {
            double m = IronsSpellsBridge.maxMana(player);
            if (m > 0) return (float) m;
        }
        return (float) StatFormulas.maxEnergy(d);
    }
}
