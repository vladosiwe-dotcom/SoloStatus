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
        d.awaitScene = false;
        d.markDirty();
        SoloStatus.LOGGER.info("Awakening sync: server start {} at {} ms {}", p.getGameProfile().getName(), now, System.currentTimeMillis());
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
        if (!(event.getEntity() instanceof ServerPlayer p) || !SoloConfig.AWAKENING_INTRO.get()) return;
        var d = Hunter.get(p);
        if (d.introPlayed && !SoloConfig.AWAKENING_EVERY_JOIN.get()) return;
        PENDING.add(p.getUUID());
        d.awaitScene = true; // synced: the client keeps the eyes closed until the scene starts
        d.markDirty();
        if (E2E_PLACE) placeInFrontOfAnotherPlayer(p);
        Scheduler.later(400, () -> {
            if (PENDING.remove(p.getUUID()) && !p.hasDisconnected() && p.isAlive()) start(p);
        });
    }

    /**
     * The client has loaded the world. The scene starts 1.5 s later: the launcher's rift light fades into the
     * closed eyes first (the client keeps them closed meanwhile), then the eyes open.
     */
    public static void onClientReady(ServerPlayer p) {
        if (PENDING.remove(p.getUUID())) Scheduler.later(30, () -> { if (!p.hasDisconnected() && p.isAlive()) start(p); });
    }

    /**
     * Two-client end-to-end check only (server environment SOLO_E2E_PLACE=1): a player who joins while someone is
     * online is put 3.5 blocks in front of that player, side-on, and the watcher is turned to face them, so the
     * watcher's client sees the whole awakening.
     */
    private static final boolean E2E_PLACE = "1".equals(System.getenv("SOLO_E2E_PLACE"));

    private static void placeInFrontOfAnotherPlayer(ServerPlayer joiner) {
        ServerPlayer watcher = null;
        for (ServerPlayer o : joiner.server.getPlayerList().getPlayers()) if (o != joiner) { watcher = o; break; }
        if (watcher == null) return;
        float yaw = watcher.getYRot();
        net.minecraft.world.phys.Vec3 at = watcher.position().add(net.minecraft.world.phys.Vec3.directionFromRotation(0, yaw).scale(3.5));
        joiner.teleportTo(watcher.serverLevel(), at.x, watcher.getY(), at.z, yaw + 90, 0);
        watcher.connection.teleport(watcher.getX(), watcher.getY(), watcher.getZ(), yaw, 25);
        SoloStatus.LOGGER.info("Awakening e2e: {} placed in front of {}", joiner.getGameProfile().getName(), watcher.getGameProfile().getName());
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
        SoloStatus.LOGGER.info("Awakening sync: server end {} at {} ms {}", p.getGameProfile().getName(), p.serverLevel().getGameTime(), System.currentTimeMillis());
        Notices.send(p, Notices.AWAKENING, Component.translatable("solostatus.notice.awakened"),
                Component.translatable(Hunter.get(p).hasClass() ? "solostatus.notice.awakened.desc_class" : "solostatus.notice.awakened.desc"));
    }

    /** Game tests: end a scene as if its time had run out. */
    public static void expireForTest(ServerPlayer p) {
        if (ACTIVE.remove(p.getUUID()) != null) finish(p);
    }
}
