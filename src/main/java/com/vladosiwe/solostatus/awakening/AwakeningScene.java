package com.vladosiwe.solostatus.awakening;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.config.SoloConfig;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.network.ModNetwork;
import com.vladosiwe.solostatus.network.Notices;
import com.vladosiwe.solostatus.network.Payloads;
import com.vladosiwe.solostatus.skill.Scheduler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The awakening scene: the hunter lies on the ground, opens the eyes, raises the head, sits up and stands.
 *
 * The server owns the scene. It starts it (first entry into the world, or /solostatus awaken), tells every
 * player who can see the hunter, and tells players who come into view later how far it has got, so everyone
 * sees the same moment. The hunter is protected while it plays. Clients draw it (AwakeningClient): with
 * GeckoLib — a player-shaped model in the hunter's own skin; without it — the vanilla model laid down and raised.
 */
@EventBusSubscriber(modid = SoloStatus.MODID)
public final class AwakeningScene {
    /** 8 s; matches the five animations in assets/solostatus/animations/awakening/player.animation.json. */
    public static final int DURATION = 160;
    private static final double VIEW = 96;

    private static final Map<UUID, Long> ACTIVE = new ConcurrentHashMap<>();
    /** First entry: waiting for the client to finish loading the world. */
    private static final java.util.Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private AwakeningScene() {}

    public static boolean active(ServerPlayer p) {
        return ACTIVE.containsKey(p.getUUID());
    }

    public static void start(ServerPlayer p) {
        long now = p.serverLevel().getGameTime();
        ACTIVE.put(p.getUUID(), now);
        // lying helpless for 8 seconds must not be a death sentence
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION + 20, 4, false, false, false));
        var d = Hunter.get(p);
        d.introPlayed = true;
        d.markDirty();
        Payloads.Awakening msg = new Payloads.Awakening(p.getId(), 0);
        for (ServerPlayer o : p.serverLevel().players()) {
            if (o == p || o.distanceToSqr(p) < VIEW * VIEW) ModNetwork.toPlayer(o, msg);
        }
    }

    /** A player came into view of a hunter in the middle of the scene: show it from where it is now. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer target) || !(event.getEntity() instanceof ServerPlayer viewer)) return;
        Long start = ACTIVE.get(target.getUUID());
        if (start == null) return;
        int elapsed = (int) (target.serverLevel().getGameTime() - start);
        if (elapsed < DURATION) ModNetwork.toPlayer(viewer, new Payloads.Awakening(target.getId(), elapsed));
    }

    /**
     * First entry into the world: the System wakes the hunter (once per profile; can be turned off in the config).
     * The scene starts when the client reports the world is loaded, so the eyes open on a ready world and never
     * during the loading screen; if a client never reports (no mod on it), it starts after 20 s anyway.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || Hunter.get(p).introPlayed || !SoloConfig.AWAKENING_INTRO.get()) return;
        PENDING.add(p.getUUID());
        Scheduler.later(400, () -> {
            if (PENDING.remove(p.getUUID()) && !p.hasDisconnected() && p.isAlive()) start(p);
        });
    }

    public static void onClientReady(ServerPlayer p) {
        if (PENDING.remove(p.getUUID()) && p.isAlive() && !Hunter.get(p).introPlayed) start(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
        PENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (Iterator<Map.Entry<UUID, Long>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) { it.remove(); continue; }
            if (p.serverLevel().getGameTime() - e.getValue() >= DURATION) {
                it.remove();
                finish(p);
            }
        }
    }

    private static void finish(ServerPlayer p) {
        Notices.send(p, Notices.AWAKENING, Component.translatable("solostatus.notice.awakened"),
                Component.translatable(Hunter.get(p).hasClass() ? "solostatus.notice.awakened.desc_class" : "solostatus.notice.awakened.desc"));
    }

    /** Game tests: end a scene as if its time had run out. */
    public static void expireForTest(ServerPlayer p) {
        if (ACTIVE.remove(p.getUUID()) != null) finish(p);
    }
}
