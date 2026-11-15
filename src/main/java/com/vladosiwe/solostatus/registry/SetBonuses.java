package com.vladosiwe.solostatus.registry;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.skill.SkillFx;
import com.vladosiwe.solostatus.stats.AttributeSystem;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Bonuses for wearing several pieces of one System armour set (2 and 4 pieces), each set made for its styles:
 *  - Vanguard (Guardian, Juggernaut, Berserker, Warlord): 2 — 8% less damage taken; 4 — +0.5 knockback resistance and
 *    melee blows give stamina back;
 *  - Stalker (Shadow, Blade Dancer, Ranger, Sharpshooter): 2 — +5% dodge; 4 — the blow after a dodge is a sure crit;
 *  - Arcanist (Elementalist, Arcanist, Cleric, Enchanter): 2 — +10% Iron's mana regeneration; 4 — +8% spell cooldown
 *    reduction (both in IronsSpellsCompat).
 * Texts: solostatus.set.&lt;set&gt;.2 / .4.
 */
public final class SetBonuses {
    private static final ResourceLocation VANGUARD_KNOCKBACK = SoloStatus.id("set_vanguard_knockback");

    private SetBonuses() {}

    public static int pieces(Player p, SystemArmor.Set set) {
        int n = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (p.getItemBySlot(slot).getItem() instanceof SystemArmor.Piece piece && piece.set() == set) n++;
        }
        return n;
    }

    public static boolean has(Player p, SystemArmor.Set set, int count) {
        return pieces(p, set) >= count;
    }

    /** Multiplier of the damage the wearer takes. */
    public static double defense(Player p) {
        return has(p, SystemArmor.Set.VANGUARD, 2) ? 0.92 : 1.0;
    }

    public static double dodge(Player p) {
        return has(p, SystemArmor.Set.STALKER, 2) ? 0.05 : 0;
    }

    public static void onDodge(ServerPlayer p, PlayerData d) {
        if (has(p, SystemArmor.Set.STALKER, 4)) SkillFx.state(d, p.serverLevel(), "stalker_strike", 40);
    }

    /** A blow by the wearer: true when it must be a crit (Stalker 4). */
    public static boolean onBlow(ServerPlayer p, PlayerData d, boolean melee, boolean skill, long now) {
        if (melee && !skill && has(p, SystemArmor.Set.VANGUARD, 4) && !d.stateActive("vanguard_cd", now)) {
            SkillFx.state(d, p.serverLevel(), "vanguard_cd", 10);
            d.energy = (float) Math.min(StatFormulas.maxEnergy(d), d.energy + 1);
        }
        if (!skill && d.stateActive("stalker_strike", now)) {
            d.timedStates.remove("stalker_strike");
            return true;
        }
        return false;
    }

    /** Vanilla attributes of the set bonuses; called with the rest of the hunter's attributes. */
    public static void applyAttributes(ServerPlayer p) {
        AttributeSystem.set(p, Attributes.KNOCKBACK_RESISTANCE, VANGUARD_KNOCKBACK, has(p, SystemArmor.Set.VANGUARD, 4) ? 0.5 : 0,
                AttributeModifier.Operation.ADD_VALUE);
    }
}
