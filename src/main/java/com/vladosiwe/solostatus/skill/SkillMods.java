package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.network.Notices;
import com.vladosiwe.solostatus.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Skill modifications: a key active skill learned to {@link #RANK} lets the hunter pick one of two modifications that
 * change how it works (not just its numbers). The first pick is free; changing it later costs a Rebirth Orb, and a
 * skill reset clears them all. Effects read the pick with {@link #of}: 0 none, 1 or 2.
 * Names and descriptions: solostatus.mod.&lt;skill&gt;.&lt;1|2&gt; and .desc.
 */
public final class SkillMods {
    public static final int RANK = 3;

    /** Two per class (the Mage's are its specializations' main skills), and the general Parry and Riposte. */
    public static final Set<String> SKILLS = Set.of(
            "whirlwind", "devastating_blow",
            "flurry", "assassinate",
            "shield_bash", "fortress",
            "power_shot", "rain_of_arrows",
            "mend", "sanctuary",
            "meteor", "gravity_well", "arcane_strike",
            "parry", "riposte");

    private SkillMods() {}

    public static boolean has(String skill) {
        return SKILLS.contains(skill);
    }

    public static int of(PlayerData d, String skill) {
        return d.skillMods.getOrDefault(skill, 0);
    }

    /** Why this pick cannot be made now (a translation key), or null. */
    public static String check(PlayerData d, String skill, int choice) {
        if (!has(skill) || (choice != 1 && choice != 2)) return "solostatus.error.no_mod";
        if (d.skillRank(skill) < RANK) return "solostatus.error.mod_rank";
        return null;
    }

    public static void choose(ServerPlayer player, String skill, int choice) {
        PlayerData d = Hunter.get(player);
        String err = check(d, skill, choice);
        if (err != null) {
            Notices.warn(player, err);
            return;
        }
        int current = of(d, skill);
        if (current == choice) return;
        if (current != 0 && !player.isCreative() && !consumeOrb(player)) { // the first pick is free, a change is not
            Notices.warn(player, "solostatus.error.no_orb");
            return;
        }
        d.skillMods.put(skill, choice);
        d.markDirty();
        SkillFx.sound(player.serverLevel(), player.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 0.8f, 1.3f);
        Notices.send(player, Notices.INFO, Component.translatable("solostatus.notice.mod_chosen"),
                Component.translatable("solostatus.notice.mod_chosen.desc", Component.translatable("solostatus.mod." + skill + "." + choice),
                        Component.translatable("solostatus.skill." + skill)));
    }

    private static boolean consumeOrb(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.RESET_ORB.get())) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }
}
