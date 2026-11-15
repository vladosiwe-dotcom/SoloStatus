package com.vladosiwe.solostatus.client;

import com.vladosiwe.solostatus.SoloStatus;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Hides concealed hunters from other players: nothing of them is drawn, neither body nor armour, weapon or name
 * (the render is cancelled before Epic Fight's own renderer, which runs later), and a faint aura of a few particles
 * shows where they stand. The hunter still sees himself.
 */
@EventBusSubscriber(modid = SoloStatus.MODID, value = Dist.CLIENT)
public final class ConcealmentClient {
    /** Entity id -> client time (ms) when its concealment ends. */
    private static final Int2LongOpenHashMap UNTIL = new Int2LongOpenHashMap();

    private ConcealmentClient() {}

    public static void set(int entity, int ticks) {
        if (ticks <= 0) UNTIL.remove(entity);
        else UNTIL.put(entity, System.currentTimeMillis() + ticks * 50L);
    }

    public static boolean concealed(int entity) {
        long until = UNTIL.getOrDefault(entity, 0L);
        if (until == 0) return false;
        if (until < System.currentTimeMillis()) {
            UNTIL.remove(entity);
            return false;
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRender(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player p) || p == Minecraft.getInstance().player || !concealed(p.getId())) return;
        event.setCanceled(true);
        if (p.getRandom().nextInt(4) == 0) { // the aura: a little dark haze at the feet and chest
            double y = p.getY() + p.getRandom().nextDouble() * p.getBbHeight();
            p.level().addParticle(ParticleTypes.SMOKE, p.getX() + (p.getRandom().nextDouble() - 0.5) * 0.5, y,
                    p.getZ() + (p.getRandom().nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
        }
    }
}
