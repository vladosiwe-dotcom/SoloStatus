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

    /** Jewellery: rarity and bonuses are rolled when it drops (chests, bosses). */
    public static final DeferredItem<com.vladosiwe.solostatus.accessory.AccessoryItem> RING = ITEMS.register("ring",
            () -> new com.vladosiwe.solostatus.accessory.AccessoryItem(com.vladosiwe.solostatus.accessory.AccessoryKind.RING, new Item.Properties()));
    public static final DeferredItem<com.vladosiwe.solostatus.accessory.AccessoryItem> AMULET = ITEMS.register("amulet",
            () -> new com.vladosiwe.solostatus.accessory.AccessoryItem(com.vladosiwe.solostatus.accessory.AccessoryKind.AMULET, new Item.Properties()));

    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(RESET_ORB.get());
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            // one of each rarity, so creative players and admins can try them
            for (var kind : com.vladosiwe.solostatus.accessory.AccessoryKind.values()) {
                for (var rarity : com.vladosiwe.solostatus.quality.QualityRarity.VALUES) {
                    var stack = new net.minecraft.world.item.ItemStack(kind == com.vladosiwe.solostatus.accessory.AccessoryKind.RING ? RING.get() : AMULET.get());
                    com.vladosiwe.solostatus.accessory.AccessorySystem.apply(stack,
                            com.vladosiwe.solostatus.accessory.AccessorySystem.roll(kind, rarity, net.minecraft.util.RandomSource.create(kind.ordinal() * 31L + rarity.ordinal())));
                    event.accept(stack);
                }
            }
        }
    }

    private ModItems() {}
}
