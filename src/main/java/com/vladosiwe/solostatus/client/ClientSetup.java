package com.vladosiwe.solostatus.client;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.client.hud.SoloHud;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Регистрация клиентских ресурсов на шине мода. Только физический клиент. */
@EventBusSubscriber(modid = SoloStatus.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(Keys.OPEN_STATUS);
        event.register(Keys.INSPECT);
        event.register(Keys.VOICE);
        for (KeyMapping k : Keys.SKILLS) event.register(k);
    }

    /** The gem of a ring/amulet (layer 1) takes the colour of its rarity. */
    @SubscribeEvent
    public static void itemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> {
            if (tint != 1) return -1;
            var data = stack.get(com.vladosiwe.solostatus.registry.ModComponents.ACCESSORY.get());
            return data == null ? 0xFFB0B8C0 : data.tier().color;
        }, com.vladosiwe.solostatus.registry.ModItems.RING.get(), com.vladosiwe.solostatus.registry.ModItems.AMULET.get());
    }

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(SoloStatus.id("hud"), SoloHud::render);
        // below everything: the launcher's rift light (if any) fades out above the closed eyes
        event.registerBelowAll(com.vladosiwe.solostatus.client.awakening.AwakeningClient.LAYER, com.vladosiwe.solostatus.client.awakening.AwakeningClient::renderLayer);
    }
}
