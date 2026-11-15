package com.vladosiwe.solostatus.compat.ironsspells;

import com.vladosiwe.solostatus.SoloStatus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

/**
 * Casting Iron's Spells spells for the System and sharing Iron's mana, through Iron's public API (checked against
 * Iron's Spells 1.21.1-3.16.3: AbstractSpell, SpellRegistry, MagicData, MagicManager, ClientMagicData). Reflection
 * keeps the build free of Iron's jar; if a name changes in another version the feature switches off with a log line.
 *
 * - A Mage skill casts its spell with CastSource.SPELLBOOK at the skill's rank as the spell level: Iron's checks and
 *   spends the mana, runs the cast time and its own cast animation, sound and effect, and starts its cooldown.
 * - Casters (Mage, Support) pay for every System skill from Iron's mana, so there is one mana bar, not two.
 */
public final class IronsSpellsBridge {
    private static boolean ready, failed;
    private static Method getSpell, attemptCast, getMaxLevel, getManaCost, getCastType, getSpellId;
    private static Method effectiveCooldown, getMagicData, getMana, setMana, isCasting;
    private static Object castSourceSpellbook;

    private IronsSpellsBridge() {}

    private static boolean init() {
        if (ready) return true;
        if (failed) return false;
        try {
            Class<?> registry = Class.forName("io.redspace.ironsspellbooks.api.registry.SpellRegistry");
            Class<?> spell = Class.forName("io.redspace.ironsspellbooks.api.spells.AbstractSpell");
            Class<?> castSource = Class.forName("io.redspace.ironsspellbooks.api.spells.CastSource");
            Class<?> magicData = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            Class<?> manager = Class.forName("io.redspace.ironsspellbooks.capabilities.magic.MagicManager");
            getSpell = registry.getMethod("getSpell", ResourceLocation.class);
            attemptCast = spell.getMethod("attemptInitiateCast", ItemStack.class, int.class, Level.class, Player.class, castSource, boolean.class, String.class);
            getMaxLevel = spell.getMethod("getMaxLevel");
            getManaCost = spell.getMethod("getManaCost", int.class);
            getCastType = spell.getMethod("getCastType");
            getSpellId = spell.getMethod("getSpellId");
            effectiveCooldown = manager.getMethod("getEffectiveSpellCooldown", spell, Player.class, castSource);
            getMagicData = magicData.getMethod("getPlayerMagicData", LivingEntity.class);
            getMana = magicData.getMethod("getMana");
            setMana = magicData.getMethod("setMana", float.class);
            isCasting = magicData.getMethod("isCasting");
            for (Object c : castSource.getEnumConstants()) if (((Enum<?>) c).name().equals("SPELLBOOK")) castSourceSpellbook = c;
            if (castSourceSpellbook == null) throw new IllegalStateException("CastSource.SPELLBOOK not found");
            ready = true;
        } catch (Throwable t) {
            failed = true;
            SoloStatus.LOGGER.warn("Iron's Spells: casting through the System is unavailable in this version ({})", t.toString());
        }
        return ready;
    }

    /** The spell object, or null if Iron's has no such spell (it answers unknown ids with its "none" spell). */
    private static Object spell(String path) throws Exception {
        Object s = getSpell.invoke(null, ResourceLocation.fromNamespaceAndPath(IronsSpellsCompat.MODID, path));
        if (s == null) return null;
        String id = String.valueOf(getSpellId.invoke(s));
        return id.endsWith(":none") ? null : s;
    }

    public static boolean available(String path) {
        try {
            return init() && spell(path) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Casts the spell at this level. False if Iron's refused (no mana, cooldown, already casting): it says why itself. */
    public static boolean cast(ServerPlayer player, String path, int level) {
        if (!init()) return false;
        try {
            Object s = spell(path);
            if (s == null) return false;
            Object data = getMagicData.invoke(null, player);
            if ((boolean) isCasting.invoke(data)) return false; // a second press would cancel the cast in progress
            int lvl = Math.max(1, Math.min(level, (int) getMaxLevel.invoke(s)));
            return (boolean) attemptCast.invoke(s, player.getMainHandItem(), lvl, player.level(), player, castSourceSpellbook, true, "mainhand");
        } catch (Exception e) {
            SoloStatus.LOGGER.warn("Iron's Spells: could not cast {}: {}", path, e.toString());
            return false;
        }
    }

    /** Whether Iron's has a cast of this player in progress (started, not finished). */
    public static boolean isCasting(ServerPlayer player) {
        if (!init()) return false;
        try {
            return (boolean) isCasting.invoke(getMagicData.invoke(null, player));
        } catch (Exception e) {
            return false;
        }
    }

    /** Cooldown Iron's gives this spell for this player (with cooldown reduction), in ticks. */
    public static int cooldownTicks(Player player, String path) {
        if (!init()) return 0;
        try {
            Object s = spell(path);
            return s == null ? 0 : (int) effectiveCooldown.invoke(null, s, player, castSourceSpellbook);
        } catch (Exception e) {
            return 0;
        }
    }

    public static int manaCost(String path, int level) {
        if (!init()) return 0;
        try {
            Object s = spell(path);
            return s == null ? 0 : (int) getManaCost.invoke(s, Math.max(1, level));
        } catch (Exception e) {
            return 0;
        }
    }

    public static int maxLevel(String path) {
        if (!init()) return 1;
        try {
            Object s = spell(path);
            return s == null ? 1 : (int) getMaxLevel.invoke(s);
        } catch (Exception e) {
            return 1;
        }
    }

    /** INSTANT, LONG or CONTINUOUS (held spells do not fit a one-press skill key). */
    public static String castType(String path) {
        if (!init()) return "";
        try {
            Object s = spell(path);
            return s == null ? "" : String.valueOf(getCastType.invoke(s));
        } catch (Exception e) {
            return "";
        }
    }

    public static ResourceLocation icon(String path) {
        return ResourceLocation.fromNamespaceAndPath(IronsSpellsCompat.MODID, "textures/gui/spell_icons/" + path + ".png");
    }

    // ---------------------------------------------------------------- mana

    /** Iron's mana of a player on the server, or -1 if unavailable. */
    public static float mana(ServerPlayer player) {
        if (!init()) return -1;
        try {
            return (float) getMana.invoke(getMagicData.invoke(null, player));
        } catch (Exception e) {
            return -1;
        }
    }

    public static void spendMana(ServerPlayer player, float amount) {
        if (!init()) return;
        try {
            Object data = getMagicData.invoke(null, player);
            setMana.invoke(data, Math.max(0f, (float) getMana.invoke(data) - amount)); // Iron's syncs it with its regeneration
        } catch (Exception e) {
            SoloStatus.LOGGER.warn("Iron's Spells: could not spend mana: {}", e.toString());
        }
    }

    /** Maximum mana: Iron's max_mana attribute (INT and gear add to it). */
    public static double maxMana(Player player) {
        var attr = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.fromNamespaceAndPath(IronsSpellsCompat.MODID, "max_mana"));
        if (attr.isEmpty() || player.getAttribute(attr.get()) == null) return 0;
        return player.getAttributeValue(attr.get());
    }

    private static Method clientMana;
    private static boolean clientFailed;

    /** Mana of the local player as Iron's client knows it, or -1. Client only. */
    public static float clientMana() {
        if (clientFailed) return -1;
        try {
            if (clientMana == null) clientMana = Class.forName("io.redspace.ironsspellbooks.player.ClientMagicData").getMethod("getPlayerMana");
            return ((Number) clientMana.invoke(null)).floatValue();
        } catch (Throwable t) {
            clientFailed = true;
            return -1;
        }
    }
}
