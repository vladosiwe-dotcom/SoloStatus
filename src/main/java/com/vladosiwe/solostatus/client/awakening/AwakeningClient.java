package com.vladosiwe.solostatus.client.awakening;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.awakening.AwakeningScene;
import com.vladosiwe.solostatus.network.Payloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side of the awakening scene, for the hunter and for everyone who watches.
 *
 * Multiplayer: the server sends each viewer (and viewers who come later, with the elapsed time) the scene of a
 * given player; each client draws that player as the scene model instead of the vanilla model while it plays.
 * Nothing changes on the player entity itself. Only the hunter's own client also gets the cinematic camera,
 * frozen controls and the fade from black.
 */
@EventBusSubscriber(modid = SoloStatus.MODID, value = Dist.CLIENT)
public final class AwakeningClient {
    public static final ResourceLocation LAYER = SoloStatus.id("awakening");
    private static final boolean GECKO = ModList.get().isLoaded("geckolib");
    private static final double LENGTH = AwakeningScene.DURATION / 20.0;

    /** entity id → scene. start = client game time minus the elapsed ticks the server reported. */
    private record Scene(long start, double elapsedAtStart, float yaw, long key) {}

    private static final Map<Integer, Scene> SCENES = new HashMap<>();
    /**
     * Scene clock: client ticks counted here, not the level's game time. The server corrects a client's game time
     * from time to time (right after joining it is often a few ticks off), and such a correction would make the
     * scene jump; this counter only moves forward, one step per client tick.
     */
    private static long clock;
    /** Scenes whose end was logged / entities still to be seen drawn as normal players (sync check in the logs). */
    private static final java.util.Set<Long> ENDED = new java.util.HashSet<>();
    private static final java.util.Set<Integer> BACK_TO_NORMAL = new java.util.HashSet<>();
    private static boolean fallbackPushed;
    private static boolean headHidden;
    private static boolean announcedFallback;

    private AwakeningClient() {}

    /** The hunter's own client told the server it is ready (world loaded); reset on every new connection. */
    private static boolean readySent;
    /** Client time (ms) when ready was sent: the eyes stay closed until the scene arrives (at most 4 s). */
    private static long awaitingSince;

    public static void onPacket(Payloads.Awakening msg) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(msg.entityId());
        float yaw = e instanceof AbstractClientPlayer p ? p.yBodyRot : 0;
        long start = clock - msg.elapsed();
        SCENES.put(msg.entityId(), new Scene(start, msg.elapsed() / 20.0, yaw, ((long) msg.entityId() << 32) ^ start));
        SoloStatus.LOGGER.info("Awakening sync: client {} start {} at {} ms {} (elapsed {})", e == mc.player ? "own" : "sees",
                e != null ? e.getName().getString() : "#" + msg.entityId(), mc.level.getGameTime() - msg.elapsed(), System.currentTimeMillis(), msg.elapsed());
        if (mc.player != null && e == mc.player) {
            awaitingSince = 0;
            // look where the body faces, so the end of the scene meets the player's own view without a turn
            mc.player.setYRot(yaw);
            mc.player.setXRot(0);
        }
    }

    /** First entry, scene not here yet: keep the eyes closed so the world never flashes before the awakening. */
    private static boolean awaiting() {
        return awaitingSince > 0 && System.currentTimeMillis() - awaitingSince < 4000;
    }

    /** Scene time in seconds for this entity, or -1 when it is not in a scene. */
    public static double time(Entity e, float partialTick) {
        Scene s = e == null ? null : SCENES.get(e.getId());
        if (s == null || e.level() == null) return -1;
        double t = (clock - s.start + partialTick) / 20.0;
        return t >= 0 && t < LENGTH ? t : -1;
    }

    public static boolean localActive() {
        var mc = Minecraft.getInstance();
        return mc.player != null && (time(mc.player, 0) >= 0 || awaiting());
    }

    /** Automated client test (Rift end-to-end run): frames of the scene saved as screenshots. Off unless SOLO_E2E_SHOTS=1. */
    private static final boolean SHOTS = "1".equals(System.getenv("SOLO_E2E_SHOTS"));
    /** Every client tick while the scene plays, plus 1.5 s after it, so CI can assemble a 20 fps video. */
    private static int shotsTaken;
    private static int afterScene = -1;
    /** The same for another player's awakening seen by this client (the multiplayer view). */
    private static int otherShots;
    private static int afterOther = -1;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) clock++;
        if (mc.level == null || mc.player == null) {
            SCENES.clear();
            readySent = false;
            awaitingSince = 0;
            return;
        }
        // World loaded and no loading screen: tell the server, which starts the first-entry scene now.
        if (!readySent && mc.screen == null && com.vladosiwe.solostatus.client.ClientState.synced) {
            readySent = true;
            if (com.vladosiwe.solostatus.client.ClientState.data.awaitScene) awaitingSince = System.currentTimeMillis();
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new Payloads.ClientReady());
        }
        if (SHOTS && mc.player != null && afterScene != 0) {
            boolean playing = time(mc.player, 0) >= 0;
            if (playing) afterScene = 30;
            else if (afterScene > 0) afterScene--;
            if (playing || afterScene > 0) {
                String name = String.format(java.util.Locale.ROOT, "awakening-v-%03d.png", shotsTaken++);
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), m -> { });
                if (shotsTaken == 1) SoloStatus.LOGGER.info("Awakening: recording frames");
            } else if (afterScene == 0 || shotsTaken > 0) {
                afterScene = 0;
                SoloStatus.LOGGER.info("Awakening: recorded {} frames", shotsTaken);
            }
        }
        if (SHOTS && mc.player != null && afterOther != 0) {
            boolean other = false;
            for (var en : SCENES.entrySet()) {
                Entity e = mc.level.getEntity(en.getKey());
                if (e != null && e != mc.player && time(e, 0) >= 0) other = true;
            }
            if (other) afterOther = 30;
            else if (afterOther > 0) afterOther--;
            if (other || afterOther > 0) {
                String name = String.format(java.util.Locale.ROOT, "awakening-o-%03d.png", otherShots++);
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), m -> { });
                if (otherShots == 1) SoloStatus.LOGGER.info("Awakening: recording another player's awakening");
            } else if (otherShots > 0) {
                afterOther = 0;
                SoloStatus.LOGGER.info("Awakening: recorded {} frames of another player's awakening", otherShots);
            }
        }
        for (var en : SCENES.entrySet()) {
            Scene sc = en.getValue();
            if (clock - sc.start >= AwakeningScene.DURATION && ENDED.add(sc.key)) {
                Entity e = mc.level.getEntity(en.getKey());
                SoloStatus.LOGGER.info("Awakening sync: client {} end {} at {} ms {}", e == mc.player ? "own" : "sees",
                        e != null ? e.getName().getString() : "#" + en.getKey(), mc.level.getGameTime(), System.currentTimeMillis());
                BACK_TO_NORMAL.add(en.getKey());
            }
        }
        SCENES.entrySet().removeIf(en -> {
            boolean over = clock - en.getValue().start > AwakeningScene.DURATION + 5;
            if (over && GECKO) com.vladosiwe.solostatus.client.awakening.geo.AwakeningGeo.forget(en.getValue().key);
            return over;
        });
    }

    // ------------------------------------------------------------------ drawing the hunter

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        AbstractClientPlayer player = (AbstractClientPlayer) event.getEntity();
        float pt = event.getPartialTick();
        double t = time(player, pt);
        if (t < 0) {
            if (BACK_TO_NORMAL.remove(player.getId())) {
                SoloStatus.LOGGER.info("Awakening sync: client sees {} drawn as a normal player at {} ms {}", player.getName().getString(),
                        player.level().getGameTime(), System.currentTimeMillis());
            }
            return;
        }
        Scene s = SCENES.get(player.getId());
        if (GECKO) {
            event.setCanceled(true); // the scene model takes the vanilla model's place (and Epic Fight's, which draws later)
            // the hunter's own eyes are in the head: draw the body without it
            boolean ownView = player == Minecraft.getInstance().player;
            com.vladosiwe.solostatus.client.awakening.geo.AwakeningGeo.render(player, s.key, s.elapsedAtStart, s.yaw, ownView,
                    event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), pt);
            return;
        }
        // Without GeckoLib: the vanilla model follows the scene's root motion (lying → sitting → standing).
        if (!announcedFallback) {
            announcedFallback = true;
            SoloStatus.LOGGER.info("Awakening: GeckoLib is not installed, the vanilla model plays a simplified scene");
        }
        player.yBodyRot = player.yBodyRotO = s.yaw;
        if (player == Minecraft.getInstance().player) { // own eyes are in the head
            event.getRenderer().getModel().head.visible = false;
            event.getRenderer().getModel().hat.visible = false;
            headHidden = true;
        }
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        fallbackPushed = true;
        float[] rot = AwakeningTimeline.at("root.rotation", t);
        float[] pos = AwakeningTimeline.at("root.position", t);
        // same space as the GeckoLib model: turned to where the hunter looks, root pivot at the hips (12 px)
        ps.mulPose(Axis.YP.rotationDegrees(180f - s.yaw));
        ps.translate(0, pos[1] / 16f + 0.75f, 0);
        ps.mulPose(Axis.XP.rotationDegrees(-rot[0]));
        ps.translate(0, -0.75f, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-(180f - s.yaw)));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (fallbackPushed) {
            fallbackPushed = false;
            event.getPoseStack().popPose();
        }
        if (headHidden) {
            headHidden = false;
            event.getRenderer().getModel().head.visible = true;
            event.getRenderer().getModel().hat.visible = true;
        }
    }

    // ------------------------------------------------------------------ the hunter's own client

    /** Camera path for the hunter, or null when no scene plays (called from CameraMixin). */
    @Nullable
    public static AwakeningCamera.Pose cameraPose(Entity entity, float partialTick) {
        var mc = Minecraft.getInstance();
        if (entity != mc.player) return null;
        double t = time(entity, partialTick);
        if (t < 0) {
            if (BACK_TO_NORMAL.remove(entity.getId())) {
                SoloStatus.LOGGER.info("Awakening sync: client own view back to normal at {} ms {}", entity.level().getGameTime(), System.currentTimeMillis());
            }
            return null;
        }
        Scene s = SCENES.get(entity.getId());
        Vec3 feet = entity.getPosition(partialTick);
        return AwakeningCamera.pose(t, LENGTH, feet, s.yaw, entity.getViewYRot(partialTick), entity.getViewXRot(partialTick),
                entity.getEyeHeight());
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!localActive()) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (localActive()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (localActive()) event.setCanceled(true);
    }

    /**
     * Only the scene's own layer is drawn while it plays (no hotbar, crosshair or System HUD). The launcher mod's
     * layers (spellironrift: the rift's light fading out) stay, drawn above the closed eyes.
     */
    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (localActive() && !LAYER.equals(event.getName()) && !"spellironrift".equals(event.getName().getNamespace())) {
            event.setCanceled(true);
        }
    }

    /** Eyelids: two dark bands from the top and bottom edges, as open as the model's lids. Closed lids let a little red light through. */
    public static void renderLayer(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double t = time(mc.player, delta.getGameTimeDeltaPartialTick(false));
        float closed;
        if (t >= 0) closed = AwakeningTimeline.at("lid_a.scale", t)[1];
        else if (awaiting()) closed = 1;
        else return;
        if (closed <= 0.001f) return;
        int w = g.guiWidth(), h = g.guiHeight();
        int band = Math.round(h / 2f * Math.min(1, closed * 1.08f));
        int lid = 0xFF0C0303;
        g.fill(0, 0, w, band, lid);
        g.fill(0, h - band, w, h, lid);
        int soft = Math.max(2, h / 18); // soft lid edges
        g.fillGradient(0, band, w, Math.min(h / 2, band + soft), lid, 0x000C0303);
        g.fillGradient(0, Math.max(h / 2, h - band - soft), w, h - band, 0x000C0303, lid);
    }
}
