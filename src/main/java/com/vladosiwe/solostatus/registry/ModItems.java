package com.vladosiwe.solostatus.registry;

import com.vladosiwe.solostatus.SoloStatus;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SoloStatus.MODID);

    /** Сфера перерождения — редкий ресурс для перераспределения навыков или характеристик. */
    public static final DeferredItem<Item> RESET_ORB = ITEMS.registerSimpleItem("reset_orb",
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(16).fireResistant());

    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(RESET_ORB.get());
        }
    }

    private ModItems() {}
}
