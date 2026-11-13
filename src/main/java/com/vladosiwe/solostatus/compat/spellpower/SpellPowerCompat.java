package com.vladosiwe.solostatus.compat.spellpower;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.stats.AttributeSystem;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

/**
 * RPG Series support (Spell Engine + Spell Power Attributes: Wizards, Paladins &amp; Priests, Rogues &amp; Warriors,
 * Archers...). No compile dependency, only vanilla registries:
 *  - school attributes spell_power:&lt;school&gt; get a multiplier from INT, the Mage class and specialisation
 *    passives, so gear spell power scales with the hunter instead of adding on top of the System's own bonus;
 *  - a spell's damage type in the spell_power namespace names its school, so magic mastery grows per school.
 * Physical RPG Series weapons (daggers, sickles, double axes, claymores, hammers, glaives) are recognised
 * by WeaponClassifier through their ids.
 */
public final class SpellPowerCompat {
    public static final String MODID = "spell_power";
    public static final String[] SCHOOLS = {"arcane", "fire", "frost", "healing", "lightning", "soul"};

    private SpellPowerCompat() {}

    public static void applyAttributes(ServerPlayer p, PlayerData d) {
        double base = Math.min(0.40, StatFormulas.magicPower(d) * 0.8);
        if (d.hunterClass == HunterClass.MAGE) base += 0.05;
        int att = d.skillRank("elemental_attunement"), flow = d.skillRank("arcane_flow"), grace = d.skillRank("divine_grace");
        for (String school : SCHOOLS) {
            double v = base;
            if (att > 0 && (school.equals("fire") || school.equals("frost") || school.equals("lightning"))) v += 0.03 * att;
            if (flow > 0 && (school.equals("arcane") || school.equals("soul"))) v += 0.02 * flow;
            if (grace > 0 && school.equals("healing")) v += 0.04 * grace;
            final double value = v;
            BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.fromNamespaceAndPath(MODID, school)).ifPresent(h ->
                    AttributeSystem.set(p, h, SoloStatus.id("sp_" + school), value, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** School of a Spell Engine spell's damage ("fire", "frost"...) or null. */
    @Nullable
    public static String school(DamageSource source) {
        return source.typeHolder().unwrapKey()
                .filter(k -> k.location().getNamespace().equals(MODID))
                .map(k -> k.location().getPath())
                .orElse(null);
    }
}
