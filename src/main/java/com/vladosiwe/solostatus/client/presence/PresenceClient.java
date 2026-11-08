package com.vladosiwe.solostatus.client.presence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.achievement.AchievementDef;
import com.vladosiwe.solostatus.achievement.Achievements;
import com.vladosiwe.solostatus.client.ui.SystemFonts;
import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.clazz.Specialization;
import com.vladosiwe.solostatus.rank.HunterRank;
import com.vladosiwe.solostatus.registry.ModSounds;
import com.vladosiwe.solostatus.stats.Stat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Окно Системы другого охотника в мире: когда игрок рядом открывает Систему, перед ним появляется
 * голографическое окно STATUS с публичными данными (без 3D-модели).
 *
 * Окно висит перед охотником на уровне груди, в той стороне, куда он смотрит (как окно Системы в аниме),
 * разворачивается из линии и поворачивается к смотрящему только по горизонтали, поэтому стоит вертикально,
 * как настоящая панель. Чтобы его было удобно читать: издалека оно немного увеличивается (до 2 раз),
 * а если подойти вплотную, плавно гаснет и не закрывает весь экран. Видно на расстоянии до 32 блоков.
 */
@EventBusSubscriber(modid = SoloStatus.MODID, value = Dist.CLIENT)
public final class PresenceClient {
    private static final class Entry {
        CompoundTag info;
        long openedAt;
        long closedAt = -1;
    }

    private static final Map<Integer, Entry> OPEN = new HashMap<>();
    private static final float SCALE = 0.0125f;
    private static final int W = 150;

    private PresenceClient() {}

    public static void update(int entityId, boolean open, CompoundTag info) {
        Minecraft mc = Minecraft.getInstance();
        if (open) {
            Entry e = OPEN.get(entityId);
            if (e == null || e.closedAt >= 0) {
                e = new Entry();
                e.openedAt = System.currentTimeMillis();
                OPEN.put(entityId, e);
                Entity owner = mc.level != null ? mc.level.getEntity(entityId) : null;
                if (owner != null) {
                    mc.level.playLocalSound(owner.getX(), owner.getY() + 1, owner.getZ(), ModSounds.PRESENCE.get(), SoundSource.PLAYERS, 0.6f, 1.2f, false);
                }
            }
            e.info = info;
        } else {
            Entry e = OPEN.get(entityId);
            if (e != null && e.closedAt < 0) e.closedAt = System.currentTimeMillis();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        OPEN.clear();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || OPEN.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<Integer, Entry>> it = OPEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Entry> me = it.next();
            Entry e = me.getValue();
            if (e.closedAt >= 0 && now - e.closedAt > 220) { it.remove(); continue; }
            if (!(mc.level.getEntity(me.getKey()) instanceof Player owner) || owner == mc.player) continue;
            if (owner.distanceToSqr(cam) > 32 * 32) continue;

            float open = Mth.clamp((now - e.openedAt) / 260f, 0f, 1f);
            float close = e.closedAt >= 0 ? 1f - Mth.clamp((now - e.closedAt) / 220f, 0f, 1f) : 1f;
            float unfold = (1 - (1 - open) * (1 - open) * (1 - open)) * close;

            // перед охотником, по направлению взгляда, чуть ниже глаз: лицо остаётся видно над окном
            Vec3 look = Vec3.directionFromRotation(0, owner.getViewYRot(pt));
            Vec3 anchor = owner.getEyePosition(pt).add(look.scale(1.0)).add(0, -0.45, 0);
            double dist = cam.distanceTo(anchor);
            float near = (float) Mth.clamp((dist - 0.9) / 0.9, 0.0, 1.0);   // вплотную окно гаснет
            float far = (float) Mth.clamp((32.0 - dist) / 6.0, 0.0, 1.0);   // на границе видимости тоже
            float alpha = near * far;
            if (alpha <= 0.02f) continue;
            float grow = (float) Mth.clamp(dist / 7.0, 1.0, 2.0);             // издалека текст остаётся читаемым
            float yaw = (float) Math.atan2(cam.x - anchor.x, cam.z - anchor.z);
            pose.pushPose();
            pose.translate(anchor.x - cam.x, anchor.y - cam.y, anchor.z - cam.z);
            pose.mulPose(Axis.YP.rotation(yaw));
            pose.scale(SCALE * grow, -SCALE * grow, SCALE * grow);
            draw(pose.last().pose(), buffers, mc.font, e.info, unfold, alpha);
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static void draw(Matrix4f m, MultiBufferSource buffers, Font font, CompoundTag info, float unfold, float alpha) {
        boolean priv = info.getBoolean("private");
        int h = priv ? 58 : 98;
        int visH = Math.max(2, (int) (h * unfold));
        int x0 = -W / 2, y0 = -h / 2 + (h - visH) / 2;
        int light = LightTexture.FULL_BRIGHT;
        VertexConsumer bg = buffers.getBuffer(RenderType.textBackground());
        // свечение и стекло
        quad(bg, m, x0 - 3, y0 - 3, W + 6, visH + 6, fade(0x3055C8FF, alpha), 0.6f, light);
        quad(bg, m, x0, y0, W, visH, fade(0xE6081426, alpha), 0.5f, light);
        quad(bg, m, x0, y0, W, 1, fade(0xFF55C8FF, alpha), 0.4f, light);
        quad(bg, m, x0, y0 + visH - 1, W, 1, fade(0xFF55C8FF, alpha), 0.4f, light);
        quad(bg, m, x0, y0, 1, visH, fade(0xFF55C8FF, alpha), 0.4f, light);
        quad(bg, m, x0 + W - 1, y0, 1, visH, fade(0xFF55C8FF, alpha), 0.4f, light);
        // текст с прозрачностью ниже ~10% шрифт рисует непрозрачным, поэтому почти погасшее окно рисуется без текста
        if (unfold < 0.97f || alpha < 0.12f) return;
        TEXT_ALPHA = alpha;

        int x = x0 + 8, y = y0 + 6;
        text(font, m, buffers, SystemFonts.bold(Component.literal("STATUS")), -font.width(SystemFonts.bold(Component.literal("STATUS"))) / 2, y, 0xFFFFFFFF, light);
        y += 12;
        HunterRank rank = HunterRank.byIndex(info.getInt("rank"));
        text(font, m, buffers, SystemFonts.bold(Component.literal(info.getString("name"))), x, y, 0xFFEEF8FF, light);
        Component lvl = SystemFonts.bold(Component.translatable("solostatus.ui.lv", info.getInt("level")));
        text(font, m, buffers, lvl, x0 + W - 8 - font.width(lvl) - 14, y, 0xFFFFD46B, light);
        text(font, m, buffers, SystemFonts.bold(Component.literal(rank.letter)), x0 + W - 16, y, rank.color, light);
        y += 11;
        AchievementDef title = Achievements.get(info.getString("title"));
        if (title != null) {
            text(font, m, buffers, SystemFonts.body(title.name()), x, y, 0xFFC79BFF, light);
            y += 10;
        }
        HunterClass hc = null;
        for (HunterClass c : HunterClass.VALUES) if (c.key.equals(info.getString("class"))) hc = c;
        Specialization spec = null;
        for (Specialization s : Specialization.VALUES) if (s.key.equals(info.getString("spec"))) spec = s;
        if (hc != null) {
            Component cls = spec == null ? hc.displayName() : Component.empty().append(hc.displayName()).append(" · ")
                    .append(info.getBoolean("advanced") ? spec.advancedName() : spec.displayName());
            text(font, m, buffers, SystemFonts.body(cls), x, y, 0xFF8FB2D2, light);
            y += 12;
        }
        if (priv) {
            text(font, m, buffers, SystemFonts.body(Component.translatable("solostatus.inspect.private")), x, y, 0xFF8FB2D2, light);
            return;
        }
        int[] stats = info.getIntArray("stats");
        Stat[] order = {Stat.STR, Stat.VIT, Stat.AGI, Stat.INT, Stat.PER, Stat.LUCK};
        int colW = (W - 16) / 2;
        for (int i = 0; i < order.length && order[i].ordinal() < stats.length; i++) {
            int sx = x + (i % 2) * colW;
            int sy = y + (i / 2) * 11;
            text(font, m, buffers, SystemFonts.bold(order[i].shortName()), sx, sy, 0xFF8FB2D2, light);
            Component v = SystemFonts.bold(Component.literal(String.valueOf(stats[order[i].ordinal()])));
            text(font, m, buffers, v, sx + colW - 10 - font.width(v), sy, 0xFFFFFFFF, light);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x, float y, float w, float h, int argb, float z, int light) {
        vc.addVertex(m, x, y, z).setColor(argb).setLight(light);
        vc.addVertex(m, x, y + h, z).setColor(argb).setLight(light);
        vc.addVertex(m, x + w, y + h, z).setColor(argb).setLight(light);
        vc.addVertex(m, x + w, y, z).setColor(argb).setLight(light);
    }

    private static float TEXT_ALPHA = 1f;

    private static int fade(int argb, float alpha) {
        int base = argb >>> 24 == 0 ? 0xFF : argb >>> 24; // цвета без альфы (0xRRGGBB) считаем непрозрачными
        int a = Math.round(base * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static void text(Font font, Matrix4f m, MultiBufferSource buffers, Component c, float x, float y, int color, int light) {
        font.drawInBatch(c, x, y, fade(color, TEXT_ALPHA), false, m, buffers, Font.DisplayMode.NORMAL, 0, light);
    }
}
