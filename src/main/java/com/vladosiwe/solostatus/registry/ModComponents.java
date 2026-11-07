package com.vladosiwe.solostatus.registry;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.quality.WeaponQuality;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SoloStatus.MODID);

    /** Качество конкретного экземпляра оружия. Не меняет id предмета — идентичность чужих предметов сохраняется. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WeaponQuality>> QUALITY =
            COMPONENTS.register("quality", () -> DataComponentType.<WeaponQuality>builder()
                    .persistent(WeaponQuality.CODEC)
                    .networkSynchronized(WeaponQuality.STREAM_CODEC)
                    .build());

    private ModComponents() {}
}
