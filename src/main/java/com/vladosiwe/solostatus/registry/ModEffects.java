package com.vladosiwe.solostatus.registry;

import com.vladosiwe.solostatus.SoloStatus;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SoloStatus.MODID);

    /**
     * Concealment (Thief): mobs lose the hunter and cannot pick him as a target; other players do not see his body,
     * armour, weapon or name, only a faint aura (client: Concealment). As an effect it reaches every tracking client.
     */
    public static final DeferredHolder<MobEffect, MobEffect> CONCEALED = EFFECTS.register("concealed",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x2B2140) {});

    private ModEffects() {}
}
