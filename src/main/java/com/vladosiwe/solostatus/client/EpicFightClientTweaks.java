package com.vladosiwe.solostatus.client;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.config.SoloConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

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
        if (!ModList.get().isLoaded("epicfight") || SoloConfig.EPIC_FIGHT_FIRST_PERSON_BODY.get()) return;
        try {
            for (ModConfig config : ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.CLIENT)) {
                if (!"epicfight".equals(config.getModId())) continue;
                var loaded = config.getLoadedConfig();
                if (loaded == null || !Boolean.TRUE.equals(loaded.config().get(KEY))) continue;
                loaded.config().set(KEY, false);
                loaded.save();
                if (config.getSpec() instanceof ModConfigSpec spec) spec.afterReload();
                SoloStatus.LOGGER.info("Epic Fight: first-person body model turned off ({} in {})", KEY, config.getFileName());
            }
        } catch (Throwable t) {
            SoloStatus.LOGGER.warn("Epic Fight: could not change {}: {}", KEY, t.toString());
        }
    }
}
