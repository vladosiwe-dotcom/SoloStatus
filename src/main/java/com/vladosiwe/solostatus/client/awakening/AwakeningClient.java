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
    private static boolean fallbackPushed;
    private static boolean announcedFallback;

    private AwakeningClient() {}

    public static void onPacket(Payloads.Awakening msg) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(msg.entityId());
        float yaw = e instanceof AbstractClientPlayer p ? p.yBodyRot : 0;
        long start = mc.level.getGameTime() - msg.elapsed();
        SCENES.put(msg.entityId(), new Scene(start, msg.elapsed() / 20.0, yaw, ((long) msg.entityId() << 32) ^ start));
    }

    /** Scene time in seconds for this entity, or -1 when it is not in a scene. */
    public static double time(Entity e, float partialTick) {
        Scene s = e == null ? null : SCENES.get(e.getId());
        if (s == null || e.level() == null) return -1;
        double t = (e.level().getGameTime() - s.start + partialTick) / 20.0;
        return t >= 0 && t < LENGTH ? t : -1;
    }

    public static boolean localActive() {
        var mc = Minecraft.getInstance();
        return mc.player != null && time(mc.player, 0) >= 0;
    }

    /** Automated client test (Rift end-to-end run): frames of the scene saved as screenshots. Off unless SOLO_E2E_SHOTS=1. */
    private static final boolean SHOTS = "1".equals(System.getenv("SOLO_E2E_SHOTS"));
    private static final double[] SHOT_TIMES = {0.4, 1.9, 2.4, 3.3, 4.6, 6.4, 7.9};
    private static int shotsTaken;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            SCENES.clear();
            return;
        }
        if (SHOTS && mc.player != null && shotsTaken < SHOT_TIMES.length) {
            double t = time(mc.player, 0);
            if (t >= SHOT_TIMES[shotsTaken]) {
                String name = "awakening-" + shotsTaken + "-" + String.format(java.util.Locale.ROOT, "%.1f", t) + "s.png";
                shotsTaken++;
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
                        m -> SoloStatus.LOGGER.info("Awakening: screenshot {}", name));
            }
        }
        SCENES.entrySet().removeIf(en -> {
            boolean over = mc.level.getGameTime() - en.getValue().start > AwakeningScene.DURATION + 5;
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
        if (t < 0) return;
        Scene s = SCENES.get(player.getId());
        if (GECKO) {
            event.setCanceled(true); // the scene model takes the vanilla model's place (and Epic Fight's, which draws later)
            com.vladosiwe.solostatus.client.awakening.geo.AwakeningGeo.render(player, s.key, s.elapsedAtStart, s.yaw,
                    event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), pt);
            return;
        }
        // Without GeckoLib: the vanilla model follows the scene's root motion (lying → sitting → standing).
        if (!announcedFallback) {
            announcedFallback = true;
            SoloStatus.LOGGER.info("Awakening: GeckoLib is not installed, the vanilla model plays a simplified scene");
        }
        player.yBodyRot = player.yBodyRotO = s.yaw;
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
    }

    // ------------------------------------------------------------------ the hunter's own client

    /** Camera path for the hunter, or null when no scene plays (called from CameraMixin). */
    @Nullable
    public static AwakeningCamera.Pose cameraPose(Entity entity, float partialTick) {
        var mc = Minecraft.getInstance();
        if (entity != mc.player) return null;
        double t = time(entity, partialTick);
        if (t < 0) return null;
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

    /** Only the scene's own layer is drawn while it plays: no hotbar, crosshair or System HUD. */
    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (localActive() && !LAYER.equals(event.getName())) event.setCanceled(true);
    }

    /** Fade from black (the eyes are still closed) and cinema bars. */
    public static void renderLayer(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double t = time(mc.player, delta.getGameTimeDeltaPartialTick(false));
        if (t < 0) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float bars = (float) Math.min(Mth.clamp(t / 0.6, 0, 1), Mth.clamp((LENGTH - t) / 0.8, 0, 1));
        int bh = Math.round(h * 0.11f * bars);
        if (bh > 0) {
            g.fill(0, 0, w, bh, 0xFF000000);
            g.fill(0, h - bh, w, h, 0xFF000000);
        }
        float dark = (float) Mth.clamp(1 - (t - 0.2) / 1.3, 0, 1); // black until the eyes start to open
        if (dark > 0) g.fill(0, 0, w, h, ((int) (dark * 255) << 24));
    }
}
