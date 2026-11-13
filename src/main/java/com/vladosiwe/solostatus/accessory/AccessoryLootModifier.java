package com.vladosiwe.solostatus.accessory;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

/**
 * Jewellery in chests of any mod (every loot table under chests/): a small chance per chest, better odds in
 * dangerous structures. Bosses drop the rarer pieces (see CombatEvents.onDrops).
 */
public class AccessoryLootModifier extends LootModifier {
    public static final MapCodec<AccessoryLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, AccessoryLootModifier::new));

    private static final String[] DANGEROUS = {"end_city", "ancient_city", "bastion", "stronghold", "trial_chambers", "woodland_mansion",
            "nether_bridge", "buried_treasure", "citadel", "catacomb", "mausoleum", "dungeon"};

    public AccessoryLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        String path = context.getQueriedLootTableId().getPath();
        if (!path.startsWith("chests/")) return loot;
        double luck = 0;
        Entity e = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (e instanceof Player p) luck = StatFormulas.qualityBonus(Hunter.get(p));
        boolean dangerous = false;
        for (String d : DANGEROUS) if (path.contains(d)) dangerous = true;
        double chance = (dangerous ? 0.12 : 0.06) * (1 + luck * 2);
        if (context.getRandom().nextDouble() < chance) {
            loot.add(AccessorySystem.create(null, dangerous ? AccessorySystem.Source.RARE_CHEST : AccessorySystem.Source.CHEST, context.getRandom(), luck));
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
