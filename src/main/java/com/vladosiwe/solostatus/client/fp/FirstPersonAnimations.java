package com.vladosiwe.solostatus.client.fp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.combat.WeaponClassifier;
import com.vladosiwe.solostatus.combat.WeaponType;
import com.vladosiwe.solostatus.config.SoloConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * The System's first-person animations, written for this mod (no other mod's animations are copied).
 *
 * - Attacks: every weapon type swings its own way: a sword slashes across, a heavy sword is raised and brought down,
 *   a dagger stabs, axes and hammers chop from above, a spear thrusts. The swing lasts as long as the weapon's
 *   attack time, so a slow weapon (or a foreign one for the class) swings slower. Vanilla's own small swing is
 *   replaced: the hand is drawn again by the game's renderer without it.
 * - Bow: the arm pulls back while drawing, trembles at full draw and kicks after the shot.
 * - Crossbow: the crossbow tilts up and shakes while it is being wound.
 * - Sprinting lowers the weapon a little; a slow breath moves it when standing.
 *
 * Client option firstPersonAnimations turns all of it off.
 */
@EventBusSubscriber(modid = SoloStatus.MODID, value = Dist.CLIENT)
public final class FirstPersonAnimations {
    private static MethodHandle renderArmWithItem;
    private static boolean lookedUp;
    /** Set while the hand is drawn again through the game's renderer, so that call is not intercepted. */
    private static boolean drawing;

    private static long attackStart = -1;
    private static float attackTicks = 10;
    private static WeaponType attackType = WeaponType.NONE;

    private static int bowUseTicks;
    private static long recoilStart = -1;
    private static float sprintBlend;

    private FirstPersonAnimations() {}

    // ------------------------------------------------------------------ clocks

    private static float now(float partialTick) {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? 0 : p.tickCount + partialTick;
    }

    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && event.getHand() == InteractionHand.MAIN_HAND) startAttack();
    }

    /** Starts a swing of the weapon in the main hand (also used by the dev screenshot run). */
    public static void startAttack() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        WeaponType type = WeaponClassifier.classify(p.getMainHandItem());
        if (!type.melee || type == WeaponType.FIST) return;
        float now = now(0);
        // a new swing restarts only after most of the previous one, so spam clicks do not jitter the hand
        if (attackStart >= 0 && now - attackStart < attackTicks * 0.6f) return;
        attackStart = (long) now;
        attackType = type;
        double speed = Math.max(0.3, p.getAttributeValue(Attributes.ATTACK_SPEED));
        attackTicks = Mth.clamp((float) (20 / speed), 5f, 24f);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        // a swing started by the game (or by Epic Fight) that our key handler did not see
        if (p.swinging && p.swingTime == 1 && (attackStart < 0 || p.tickCount - attackStart > 2)) startAttack();

        boolean drawingBow = p.isUsingItem() && p.getUseItem().getItem() instanceof BowItem;
        if (drawingBow) bowUseTicks = p.getTicksUsingItem();
        else if (bowUseTicks > 0) {
            if (bowUseTicks >= 3) recoilStart = p.tickCount; // the arrow left the bow
            bowUseTicks = 0;
        }
        sprintBlend += ((p.isSprinting() ? 1f : 0f) - sprintBlend) * 0.2f;
    }

    // ------------------------------------------------------------------ rendering

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (drawing || !SoloConfig.FIRST_PERSON_ANIMATIONS.get()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isScoping()) return;
        ItemStack stack = event.getItemStack();
        boolean main = event.getHand() == InteractionHand.MAIN_HAND;
        HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float t = now(event.getPartialTick());
        PoseStack ps = event.getPoseStack();

        // bow and crossbow: added on top of the game's own transform for using the item
        if (player.isUsingItem() && player.getUsedItemHand() == event.getHand()) {
            if (stack.getItem() instanceof BowItem) {
                float pull = Math.min(1f, (player.getTicksUsingItem() + event.getPartialTick()) / 20f);
                float full = pull >= 1f ? 1f : 0f;
                ps.translate(0, 0, 0.07f * pull);
                ps.translate(Mth.sin(t * 1.9f) * 0.0045f * full, Mth.cos(t * 2.6f) * 0.0045f * full, 0);
                ps.mulPose(Axis.ZP.rotationDegrees(-4f * pull * side));
            } else if (stack.getItem() instanceof CrossbowItem) {
                float wind = Math.min(1f, (player.getTicksUsingItem() + event.getPartialTick()) / 25f);
                ps.translate(Mth.sin(t * 3.1f) * 0.003f * wind, 0.04f * wind, 0);
                ps.mulPose(Axis.XP.rotationDegrees(-12f * wind));
            }
            return;
        }

        // bow recoil after a shot
        if (recoilStart >= 0 && stack.getItem() instanceof BowItem) {
            float r = (t - recoilStart) / 7f;
            if (r >= 1f) recoilStart = -1;
            else {
                float k = (float) Math.sin(r * Math.PI) * (1 - r);
                ps.translate(0, 0.03f * k, -0.12f * k);
                ps.mulPose(Axis.XP.rotationDegrees(-10f * k));
            }
        }

        idle(ps, player, t, side);

        // melee attack: replace the game's swing with the weapon's own
        if (!main) return;
        float progress = attackStart < 0 ? 1f : (t - attackStart) / attackTicks;
        if (progress >= 1f || progress < 0f) {
            attackStart = progress >= 1f ? -1 : attackStart;
            return;
        }
        MethodHandle draw = handle();
        if (draw == null) return;
        event.setCanceled(true);
        ps.pushPose();
        swing(ps, attackType, progress, side);
        drawing = true;
        try {
            ItemInHandRenderer renderer = mc.getEntityRenderDispatcher().getItemInHandRenderer();
            draw.invoke(renderer, (AbstractClientPlayer) player, event.getPartialTick(), event.getInterpolatedPitch(), event.getHand(),
                    0f, stack, event.getEquipProgress(), ps, (MultiBufferSource) event.getMultiBufferSource(), event.getPackedLight());
        } catch (Throwable e) {
            renderArmWithItem = null; // something changed in the renderer: fall back to the game's own swing
            SoloStatus.LOGGER.warn("First-person animations disabled: {}", e.toString());
        } finally {
            drawing = false;
            ps.popPose();
        }
    }

    /** Breathing when standing and a lowered weapon when sprinting. */
    private static void idle(PoseStack ps, LocalPlayer player, float t, int side) {
        boolean still = player.getDeltaMovement().horizontalDistanceSqr() < 1.0e-4;
        if (still) ps.translate(0, Mth.sin(t * 0.07f) * 0.006f, 0);
        if (sprintBlend > 0.01f) {
            ps.translate(0.02f * side * sprintBlend, -0.06f * sprintBlend, 0.02f * sprintBlend);
            ps.mulPose(Axis.XP.rotationDegrees(12f * sprintBlend));
            ps.mulPose(Axis.YP.rotationDegrees(-8f * side * sprintBlend));
        }
    }

    // ------------------------------------------------------------------ swings

    /**
     * One pose of a swing. Keys: wind-up, strike, back to rest; each key is (x, y, z, pitch, yaw, roll) and they are
     * blended smoothly. Rotations turn around a point near the shoulder, so the weapon sweeps instead of orbiting the eye.
     */
    private static void swing(PoseStack ps, WeaponType type, float p, int side) {
        float[] wind, strike;
        float windEnd, strikeEnd;
        switch (type) {
            case GREATSWORD -> { // raised high, brought down hard and across
                wind = new float[]{0.05f, 0.22f, 0.05f, -50f, 10f, 18f};
                strike = new float[]{-0.22f, -0.28f, -0.18f, 55f, -28f, -20f};
                windEnd = 0.38f; strikeEnd = 0.62f;
            }
            case DAGGER -> { // quick stab forward
                wind = new float[]{0.04f, -0.02f, 0.14f, 12f, 6f, 0f};
                strike = new float[]{-0.06f, 0.05f, -0.42f, -12f, -6f, 0f};
                windEnd = 0.2f; strikeEnd = 0.42f;
            }
            case AXE, MACE -> { // chop from above
                wind = new float[]{0.03f, 0.18f, 0.06f, -42f, 8f, 10f};
                strike = new float[]{-0.08f, -0.26f, -0.16f, 38f, -12f, -8f};
                windEnd = 0.32f; strikeEnd = 0.56f;
            }
            case SPEAR -> { // thrust
                wind = new float[]{0.02f, 0.02f, 0.2f, 4f, 4f, 0f};
                strike = new float[]{-0.08f, 0.04f, -0.58f, -4f, -4f, 0f};
                windEnd = 0.28f; strikeEnd = 0.5f;
            }
            case STAFF -> { // light arc
                wind = new float[]{0.08f, 0.06f, 0.02f, -15f, 25f, 20f};
                strike = new float[]{-0.18f, -0.06f, -0.12f, 10f, -40f, -15f};
                windEnd = 0.25f; strikeEnd = 0.52f;
            }
            default -> { // sword: slash across from right to left
                wind = new float[]{0.14f, 0.08f, 0.04f, -12f, 35f, 22f};
                strike = new float[]{-0.3f, -0.08f, -0.16f, 14f, -55f, -26f};
                windEnd = 0.25f; strikeEnd = 0.52f;
            }
        }
        float[] pose = new float[6];
        if (p < windEnd) lerp(pose, null, wind, smooth(p / windEnd));
        else if (p < strikeEnd) lerp(pose, wind, strike, fast((p - windEnd) / (strikeEnd - windEnd)));
        else lerp(pose, strike, null, smooth((p - strikeEnd) / (1 - strikeEnd)));

        float px = 0.38f * side, py = -0.5f, pz = -0.42f; // pivot near the shoulder
        ps.translate(pose[0] * side, pose[1], pose[2]);
        ps.translate(px, py, pz);
        // in view space a positive X turn lowers the hand and a positive Y turn swings it towards the centre:
        // the keys are written as "raise" and "swing out", so both are negated here
        ps.mulPose(Axis.XP.rotationDegrees(-pose[3]));
        ps.mulPose(Axis.YP.rotationDegrees(-pose[4] * side));
        ps.mulPose(Axis.ZP.rotationDegrees(pose[5] * side));
        ps.translate(-px, -py, -pz);
    }

    private static void lerp(float[] out, float[] a, float[] b, float k) {
        for (int i = 0; i < out.length; i++) {
            float va = a == null ? 0 : a[i], vb = b == null ? 0 : b[i];
            out[i] = va + (vb - va) * k;
        }
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** Fast start, soft stop: the strike itself. */
    private static float fast(float x) {
        x = Mth.clamp(x, 0, 1);
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private static MethodHandle handle() {
        if (lookedUp) return renderArmWithItem;
        lookedUp = true;
        try {
            Method m = ItemInHandRenderer.class.getDeclaredMethod("renderArmWithItem", AbstractClientPlayer.class, float.class, float.class,
                    InteractionHand.class, float.class, ItemStack.class, float.class, PoseStack.class, MultiBufferSource.class, int.class);
            m.setAccessible(true);
            renderArmWithItem = MethodHandles.lookup().unreflect(m);
        } catch (Throwable e) {
            SoloStatus.LOGGER.warn("First-person attack animations unavailable: {}", e.toString());
        }
        return renderArmWithItem;
    }
}
