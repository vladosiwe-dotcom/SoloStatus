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

    /**
     * Model states of the legendary bow (drawn: pull, pulling — by its own draw time) and shield (blocking), under
     * vanilla's predicate names so their item models read like vanilla's.
     */
    @SubscribeEvent
    public static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            var bow = com.vladosiwe.solostatus.registry.LegendaryWeapons.Kind.SKYHUNT_BOW.item();
            net.minecraft.client.renderer.item.ItemProperties.register(bow, net.minecraft.resources.ResourceLocation.withDefaultNamespace("pull"),
                    (stack, level, entity, seed) -> com.vladosiwe.solostatus.registry.LegendaryWeapons.pull(stack, entity));
            net.minecraft.client.renderer.item.ItemProperties.register(bow, net.minecraft.resources.ResourceLocation.withDefaultNamespace("pulling"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1f : 0f);
            var shield = com.vladosiwe.solostatus.registry.LegendaryWeapons.Kind.LAST_BASTION.item();
            net.minecraft.client.renderer.item.ItemProperties.register(shield, net.minecraft.resources.ResourceLocation.withDefaultNamespace("blocking"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1f : 0f);
        });
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
    }
}
