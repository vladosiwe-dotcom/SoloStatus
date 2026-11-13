package com.vladosiwe.solostatus.quality;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.vladosiwe.solostatus.combat.WeaponClassifier;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.stats.StatFormulas;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/** Глобальный модификатор лута: оружие в сундуках получает качество Common–Rare (с учётом LUCK открывшего). */
public class QualityLootModifier extends LootModifier {
    public static final MapCodec<QualityLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, QualityLootModifier::new));

    public QualityLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!QualitySystem.enabled()) return loot;
        if (!context.getQueriedLootTableId().getPath().startsWith("chests/")) return loot;
        double luck = 0;
        Entity e = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (e instanceof Player p) luck = StatFormulas.qualityBonus(Hunter.get(p));
        for (ItemStack stack : loot) {
            if (WeaponClassifier.isQualityWeapon(stack) && QualitySystem.get(stack) == null) {
                QualitySystem.roll(stack, QualitySystem.Source.CHEST, context.getRandom(), luck);
            }
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
