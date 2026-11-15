package com.vladosiwe.solostatus.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.client.ui.SystemFonts;
import com.vladosiwe.solostatus.config.SoloConfig;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.Tags;
import org.joml.Matrix4f;

/**
 * The System's health bar above a mob: shown for a few seconds after the mob is hurt, and while it is under the
 * crosshair. It stands at the mob's own height (its bounding box, so tall and scaled mobs such as wither skeletons
 * are right), turns to face the camera, and shows lost health as a fading trail. Bosses keep their vanilla boss bar.
 */
@EventBusSubscriber(modid = SoloStatus.MODID, value = Dist.CLIENT)
public final class MobHealthBars {
    private static final long SHOW_MS = 5000;
    private static final double MAX_DISTANCE = 32;
    private static final int W = 40, H = 4;

    /** When the bar of an entity id was last needed, and the health its trail is still showing. */
    private static final Int2LongOpenHashMap SHOWN_AT = new Int2LongOpenHashMap();
    private static final Int2FloatOpenHashMap TRAIL = new Int2FloatOpenHashMap();

    private MobHealthBars() {}

    @SubscribeEvent
    public static void onRender(RenderLivingEvent.Post<?, ?> event) {
        if (!SoloConfig.MOB_HEALTH_BARS.get()) return;
        LivingEntity mob = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mob instanceof Player || mob.isInvisible() || !mob.isAlive()) return;
        if (mob.getType().is(Tags.EntityTypes.BOSSES)) return;
        if (mob.distanceToSqr(mc.player) > MAX_DISTANCE * MAX_DISTANCE) return;

        long now = System.currentTimeMillis();
        int id = mob.getId();
        if (mob.hurtTime > 0 || mc.crosshairPickEntity == mob) SHOWN_AT.put(id, now);
        long since = now - SHOWN_AT.getOrDefault(id, Long.MIN_VALUE / 2);
        if (since > SHOW_MS) {
            TRAIL.remove(id);
            return;
        }
        float alpha = since > SHOW_MS - 500 ? (SHOW_MS - since) / 500f : 1f;

        float max = Math.max(1, mob.getMaxHealth());
        float hp = Math.max(0, Math.min(max, mob.getHealth()));
        float trail = Math.max(hp, TRAIL.getOrDefault(id, hp));
        trail += (hp - trail) * 0.08f; // the lost part shrinks after the hit
        TRAIL.put(id, trail);

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(0, mob.getBbHeight() + 0.45, 0);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(0.025f, -0.025f, 0.025f);
        // each layer sits a little nearer the camera (+z here) than the one behind it
        Matrix4f m = ps.last().pose();
        MultiBufferSource buffers = event.getMultiBufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        int light = event.getPackedLight();

        float x0 = -W / 2f, y0 = 0;
        quad(vc, m, x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, 0f, argb(0xC0081020, alpha), light);   // frame
        quad(vc, m, x0, y0, x0 + W, y0 + H, 0.1f, argb(0xE0141E2C, alpha), light);              // empty
        quad(vc, m, x0, y0, x0 + W * trail / max, y0 + H, 0.2f, argb(0xD0E8E0D0, alpha), light); // just lost
        float f = hp / max;
        int color = f > 0.5f ? 0xFF4FD08A : f > 0.25f ? 0xFFE8C860 : 0xFFE8604A;
        quad(vc, m, x0, y0, x0 + W * f, y0 + H, 0.3f, argb(color, alpha), light);
        quad(vc, m, x0, y0, x0 + W * f, y0 + 1, 0.4f, argb(0x60FFFFFF, alpha), light);          // shine

        Font font = mc.font;
        Component text = SystemFonts.bold(Math.round(hp) + " / " + Math.round(max));
        float tx = -font.width(text) / 2f;
        font.drawInBatch(text, tx, y0 - 10, argb(0xFFE8F4FF, alpha), false, m, buffers, Font.DisplayMode.NORMAL, 0, light);
        ps.popPose();

        if (SHOWN_AT.size() > 512) SHOWN_AT.int2LongEntrySet().removeIf(e -> now - e.getLongValue() > SHOW_MS);
        if (TRAIL.size() > 512) TRAIL.clear();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb, int light) {
        if (x1 <= x0) return;
        vc.addVertex(m, x0, y0, z).setColor(argb).setLight(light);
        vc.addVertex(m, x0, y1, z).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y1, z).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y0, z).setColor(argb).setLight(light);
    }

    private static int argb(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (Math.max(a, 4) << 24) | (argb & 0xFFFFFF);
    }
}
