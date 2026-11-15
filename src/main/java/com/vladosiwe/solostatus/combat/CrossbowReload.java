package com.vladosiwe.solostatus.combat;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.skill.Scheduler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Crossbowman's Auto-reload: after a bolt leaves the crossbow, the System winds it again by itself a moment later,
 * taking ammunition from the inventory the way loading by hand does (free in creative). Faster with each rank.
 */
@EventBusSubscriber(modid = SoloStatus.MODID)
public final class CrossbowReload {
    private CrossbowReload() {}

    @SubscribeEvent
    public static void onShot(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof ServerPlayer p) || arrow.tickCount > 0) return;
        if (!(arrow.getWeaponItem().getItem() instanceof CrossbowItem)) return;
        int rank = Hunter.get(p).skillRank("auto_reload");
        if (rank <= 0) return;
        Scheduler.later(Math.max(4, 26 - rank * 4), () -> reload(p));
    }

    /** Winds a fired crossbow in the player's hands again (public for the game test). */
    public static void reload(ServerPlayer p) {
        if (!p.isAlive()) return;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack crossbow = p.getItemInHand(hand);
            if (!(crossbow.getItem() instanceof CrossbowItem) || CrossbowItem.isCharged(crossbow)) continue;
            ItemStack ammo = p.getProjectile(crossbow);
            if (ammo.isEmpty() && !p.getAbilities().instabuild) return;
            ItemStack loaded = ammo.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.ARROW) : ammo.copyWithCount(1);
            if (!p.getAbilities().instabuild && !ammo.isEmpty()) ammo.shrink(1);
            crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(loaded));
            p.level().playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
            return;
        }
    }
}
