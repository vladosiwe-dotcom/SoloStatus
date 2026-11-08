package com.vladosiwe.solostatus.registry;

import com.mojang.serialization.MapCodec;
import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.quality.QualityLootModifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SoloStatus.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<QualityLootModifier>> QUALITY =
            SERIALIZERS.register("weapon_quality", () -> QualityLootModifier.CODEC);
    public static final net.neoforged.neoforge.registries.DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<com.vladosiwe.solostatus.accessory.AccessoryLootModifier>> ACCESSORIES =
            SERIALIZERS.register("accessories", () -> com.vladosiwe.solostatus.accessory.AccessoryLootModifier.CODEC);

    private ModLootModifiers() {}
}
