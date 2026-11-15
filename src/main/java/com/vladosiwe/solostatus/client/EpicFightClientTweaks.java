package com.vladosiwe.solostatus.client;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.config.SoloConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Epic Fight draws the whole player model in first person (its client option ingame.first_person_model). The
 * body and armour then poke into the corners of the screen. Unless the player asks for it in Solo Status' client
 * config, this turns the option off in Epic Fight's own client config once the game is up (plain first-person
 * hands; Epic Fight animations are still seen in third person and by other players).
 */
@EventBusSubscriber(modid = SoloStatus.MODID, value = Dist.CLIENT)
public final class EpicFightClientTweaks {
    private static final String KEY = "ingame.first_person_model";
    private static boolean done;

    private EpicFightClientTweaks() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (done) return;
        done = true;
        if (!ModList.get().isLoaded("epicfight")) return;
        try {
            for (ModConfig config : ModConfigs.getModConfigs("epicfight")) { // FML 4 (NeoForge 21.1) API
                if (config.getType() != ModConfig.Type.CLIENT) continue;
                var loaded = config.getLoadedConfig();
                if (loaded == null) continue;
                boolean changed = false;
                if (!SoloConfig.EPIC_FIGHT_FIRST_PERSON_BODY.get() && Boolean.TRUE.equals(loaded.config().get(KEY))) {
                    loaded.config().set(KEY, false);
                    changed = true;
                    SoloStatus.LOGGER.info("Epic Fight: first-person body model turned off ({} in {})", KEY, config.getFileName());
                }
                if (SoloConfig.MOB_HEALTH_BARS.get()) changed |= hideHealthBars(loaded.config(), "", config.getFileName());
                if (changed) loaded.save(); // also fires the config reloading event, so Epic Fight picks the values up at once
            }
        } catch (Throwable t) {
            SoloStatus.LOGGER.warn("Epic Fight: could not change its client config: {}", t.toString());
        }
    }

    /**
     * The System draws its own mob health bars (MobHealthBars), sized by each mob's real height; Epic Fight's bar sits
     * wrong on tall and scaled mobs such as wither skeletons. Every Epic Fight client option about health bars is
     * switched off: a boolean becomes false, a choice becomes its "none"/"off"/"never" value. The names are looked up
     * rather than hard-coded, so a renamed option in another Epic Fight version is still found (and logged).
     */
    private static boolean hideHealthBars(com.electronwill.nightconfig.core.UnmodifiableConfig cfg, String prefix, String file) {
        boolean changed = false;
        for (var entry : cfg.entrySet()) {
            String path = prefix + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig sub) {
                changed |= hideHealthBars(sub, path + ".", file);
                continue;
            }
            String key = path.toLowerCase(java.util.Locale.ROOT);
            if (!(key.contains("health") && (key.contains("bar") || key.contains("indicator")))) continue;
            Object off = null;
            if (Boolean.TRUE.equals(value)) off = false;
            else if (value instanceof String s && !isOff(s)) off = offValue(s);
            else if (value instanceof Enum<?> e && !isOff(e.name())) off = offEnum(e);
            if (off == null) continue;
            if (cfg instanceof com.electronwill.nightconfig.core.Config writable) {
                writable.set(entry.getKey(), off);
                changed = true;
                SoloStatus.LOGGER.info("Epic Fight: health bar option {} = {} -> {} ({})", path, value, off, file);
            }
        }
        return changed;
    }

    private static boolean isOff(String v) {
        String s = v.toLowerCase(java.util.Locale.ROOT);
        return s.equals("none") || s.equals("off") || s.equals("never") || s.equals("disabled") || s.equals("false");
    }

    private static Object offValue(String current) {
        return current.equals(current.toUpperCase(java.util.Locale.ROOT)) ? "NONE" : "none";
    }

    private static Object offEnum(Enum<?> e) {
        for (Object c : e.getDeclaringClass().getEnumConstants()) if (isOff(((Enum<?>) c).name())) return c;
        return null;
    }
}
