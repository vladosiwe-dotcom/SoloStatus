package com.vladosiwe.solostatus.compat.epicfight;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.vladosiwe.solostatus.SoloStatus;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Skills come from the System only: Epic Fight's skill books never appear. They are taken out of every loot table
 * (chests, mob drops, fishing: this modifier runs after Epic Fight's own, since Solo Status loads after it), and a
 * skill book dropped into the world any other way does not spawn.
 */
public class NoSkillBooks extends LootModifier {
    public static final MapCodec<NoSkillBooks> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, NoSkillBooks::new));

    public NoSkillBooks(LootItemCondition[] conditions) {
        super(conditions);
    }

    public static boolean isSkillBook(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("epicfight") && id.getPath().contains("skillbook");
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        loot.removeIf(NoSkillBooks::isSkillBook);
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    @EventBusSubscriber(modid = SoloStatus.MODID)
    public static final class Drops {
        private Drops() {}

        @SubscribeEvent
        public static void onJoin(EntityJoinLevelEvent event) {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item && isSkillBook(item.getItem())) {
                event.setCanceled(true);
            }
        }
    }
}
