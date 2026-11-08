package com.vladosiwe.solostatus.compat.epicfight;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.combat.WeaponClassifier;
import com.vladosiwe.solostatus.compat.CompatManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Keeps Epic Fight's mode in step with the item in the main hand (see EpicFightCompat.matchModeToHeldItem). */
@EventBusSubscriber(modid = SoloStatus.MODID)
public final class EpicFightModeEvents {
    private EpicFightModeEvents() {}

    @SubscribeEvent
    public static void onHeldItem(LivingEquipmentChangeEvent event) {
        if (event.getSlot() != EquipmentSlot.MAINHAND || !CompatManager.epicFightLoaded() || !(event.getEntity() instanceof ServerPlayer p)) return;
        boolean before = WeaponClassifier.isCombatWeapon(event.getFrom()), now = WeaponClassifier.isCombatWeapon(event.getTo());
        if (before != now) EpicFightCompat.matchModeToHeldItem(p, now);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (CompatManager.epicFightLoaded() && event.getEntity() instanceof ServerPlayer p) {
            EpicFightCompat.matchModeToHeldItem(p, WeaponClassifier.isCombatWeapon(p.getMainHandItem()));
        }
    }
}
