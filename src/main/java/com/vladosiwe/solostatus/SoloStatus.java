package com.vladosiwe.solostatus;

import com.mojang.logging.LogUtils;
import com.vladosiwe.solostatus.compat.CompatManager;
import com.vladosiwe.solostatus.config.SoloConfig;
import com.vladosiwe.solostatus.registry.ModAttachments;
import com.vladosiwe.solostatus.registry.ModComponents;
import com.vladosiwe.solostatus.registry.ModItems;
import com.vladosiwe.solostatus.registry.ModLootModifiers;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * Точка входа Solo Status System.
 * Здесь только регистрация; вся логика разнесена по независимым системам (см. docs/ARCHITECTURE.md).
 */
@Mod(SoloStatus.MODID)
public final class SoloStatus {
    public static final String MODID = "solostatus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SoloStatus(IEventBus modBus, ModContainer container) {
        ModAttachments.ATTACHMENTS.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModLootModifiers.SERIALIZERS.register(modBus);

        container.registerConfig(ModConfig.Type.SERVER, SoloConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, SoloConfig.CLIENT_SPEC);

        modBus.addListener(this::commonSetup);
        modBus.addListener(ModItems::addToCreativeTabs);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CompatManager::init);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
