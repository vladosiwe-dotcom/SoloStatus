package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.network.Payloads;
import com.vladosiwe.solostatus.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Thief's Concealment: everyone loses sight of the hunter. Mobs drop him as a target and cannot pick him again; other
 * players see neither body, armour, weapon nor name, only a faint aura where he stands (ConcealmentClient). The
 * first blow he strikes ends it, with extra damage (CombatEvents).
 */
@EventBusSubscriber(modid = SoloStatus.MODID)
public final class Concealment {
    private Concealment() {}

    public static boolean active(Player p) {
        return p.hasEffect(ModEffects.CONCEALED);
    }

    public static void start(ServerPlayer p, int ticks) {
        p.addEffect(new MobEffectInstance(ModEffects.CONCEALED, ticks, 0, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, false));
        for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p)) m.setTarget(null);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new Payloads.ConcealSync(p.getId(), ticks));
    }

    public static void end(ServerPlayer p) {
        if (!active(p)) return;
        p.removeEffect(ModEffects.CONCEALED);
        p.removeEffect(MobEffects.INVISIBILITY);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new Payloads.ConcealSync(p.getId(), 0));
    }

    /** A mob cannot choose a concealed hunter as its target. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof Player p && active(p)) event.setCanceled(true);
    }

    /** When it runs out on its own, the other clients are told too. */
    @SubscribeEvent
    public static void onExpire(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().getEffect().equals(ModEffects.CONCEALED)
                && event.getEntity() instanceof ServerPlayer p) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new Payloads.ConcealSync(p.getId(), 0));
        }
    }
}
