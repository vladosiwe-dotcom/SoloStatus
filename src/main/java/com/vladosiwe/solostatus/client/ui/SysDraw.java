package com.vladosiwe.solostatus.client.ui;

import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Примитивы окна Системы: белое пространство, стеклянное окно с многослойным свечением,
 * табличка заголовка, разделители, полосы. Всё рисуется процедурно — без растровых текстур,
 * поэтому края и свечение одинаково чистые на любом масштабе интерфейса.
 */
public final class SysDraw {
    // Палитра Системы
    public static final int GLASS = 0xE6081426;
    public static final int GLASS_HI = 0xF00C1E36;
    public static final int EDGE = 0xFF55C8FF;
    public static final int INK = 0xFFEEF8FF;
    public static final int DIM = 0xFF8FB2D2;
    public static final int GOLD = 0xFFFFD46B;
    public static final int VIOLET = 0xFFC79BFF;
    public static final int RED = 0xFFFF6D6D;
    public static final int GREEN = 0xFF7DFF9B;
    public static final int LOCK = 0xFF4A5A6A;

    private SysDraw() {}

    public static int a(int color, float alpha) {
        int base = (color >>> 24) & 0xFF;
        return (Mth.clamp((int) (base * alpha), 0, 255) << 24) | (color & 0xFFFFFF);
    }

    /** Текст с прозрачностью (шрифт Minecraft считает альфу < 4 непрозрачной — такие кадры пропускаем). */
    public static void text(GuiGraphics g, Font f, Component c, int x, int y, int color, float alpha) {
        if (alpha < 0.03f) return;
        g.drawString(f, c, x, y, a(color, alpha), false);
    }

    public static void textRight(GuiGraphics g, Font f, Component c, int right, int y, int color, float alpha) {
        text(g, f, c, right - f.width(c), y, color, alpha);
    }

    public static void textCenter(GuiGraphics g, Font f, Component c, int cx, int y, int color, float alpha) {
        text(g, f, c, cx - f.width(c) / 2, y, color, alpha);
    }

    // ============================== Белое пространство ==============================

    /**
     * Белая пустота с перспективной сеткой пола. t — прогресс появления (0..1),
     * vpX/horizon — точка схода (под охотником).
     */
    public static void whiteSpace(GuiGraphics g, int w, int h, float t, int vpX, int horizon) {
        g.fill(0, 0, w, h, a(0xFFF5FAFF, t));
        // лёгкая дымка у горизонта и голубоватые края — глубина пространства
        g.fillGradient(0, 0, w, horizon, a(0x00D6E8FA, t), a(0x40D6E8FA, t));
        g.fillGradient(0, horizon, w, h, a(0x30C8DDF2, t), a(0x00C8DDF2, t));
        float gridA = Mth.clamp((t - 0.35f) / 0.65f, 0f, 1f);
        if (gridA <= 0) return;
        // горизонтальные линии с перспективным шагом
        for (int k = 1; k <= 14; k++) {
            int y = horizon + (int) (Math.pow(k, 1.75) * 1.6);
            if (y >= h) break;
            float la = Math.min(1f, k / 9f) * 0.45f * gridA;
            g.fill(0, y, w, y + 1, a(0xFF7FAAD6, la));
        }
        // сходящиеся линии
        g.pose().pushPose();
        g.pose().translate(vpX, horizon, 0);
        for (int i = -12; i <= 12; i++) {
            g.pose().pushPose();
            g.pose().mulPose(Axis.ZP.rotationDegrees(i * 6.5f));
            g.fillGradient(0, 0, 1, h * 2, a(0x007FAAD6, gridA), a(0x707FAAD6, gridA));
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    /** Мягкая тень под охотником. */
    public static void shadow(GuiGraphics g, int cx, int y, int radius, float alpha) {
        for (int i = 0; i < 6; i++) {
            int rw = radius - i * radius / 7;
            int rh = Math.max(1, 6 - i);
            g.fill(cx - rw, y - rh / 2, cx + rw, y + rh / 2 + 1, a(0x1A0D2238, alpha));
        }
    }

    // ============================== Окно ==============================

    /**
     * Стеклянное окно Системы. unfold (0..1): сначала яркая линия по центру, затем раскрытие по высоте.
     * Возвращает текущую видимую высоту.
     */
    public static int window(GuiGraphics g, int x, int y, int w, int h, float unfold, float alpha) {
        float u = easeOut(Mth.clamp(unfold, 0f, 1f));
        int vis = Math.max(2, (int) (h * u));
        int top = y + (h - vis) / 2;
        // многослойное свечение
        for (int i = 4; i >= 1; i--) {
            g.renderOutline(x - i, top - i, w + i * 2, vis + i * 2, a(0x3355C8FF, alpha * (1f - i / 5f)));
        }
        g.fill(x, top, x + w, top + vis, a(GLASS, alpha));
        g.fillGradient(x, top, x + w, top + Math.min(vis, 40), a(0x3055C8FF, alpha), a(0x0055C8FF, alpha));
        g.renderOutline(x, top, w, vis, a(EDGE, alpha));
        if (vis > 8) g.renderOutline(x + 3, top + 3, w - 6, vis - 6, a(0x3355C8FF, alpha));
        // уголки
        int c = 7;
        int ca = a(0xFFBFEFFF, alpha);
        g.fill(x - 2, top - 2, x + c, top - 1, ca);
        g.fill(x - 2, top - 2, x - 1, top + c, ca);
        g.fill(x + w - c, top - 2, x + w + 2, top - 1, ca);
        g.fill(x + w + 1, top - 2, x + w + 2, top + c, ca);
        g.fill(x - 2, top + vis + 1, x + c, top + vis + 2, ca);
        g.fill(x - 2, top + vis - c, x - 1, top + vis + 2, ca);
        g.fill(x + w - c, top + vis + 1, x + w + 2, top + vis + 2, ca);
        g.fill(x + w + 1, top + vis - c, x + w + 2, top + vis + 2, ca);
        // вспышка раскрытия
        if (unfold < 1f) {
            float flash = 1f - u;
            g.fill(x - 6, top + vis / 2 - 1, x + w + 6, top + vis / 2 + 1, a(0xFFFFFFFF, flash * alpha));
        }
        return vis;
    }

    /** Табличка заголовка на верхней кромке окна (как STATUS / SKILLS в аниме). */
    public static void titlePlate(GuiGraphics g, Font f, Component text, int cx, int y, float alpha) {
        int tw = f.width(text) + 26;
        int x = cx - tw / 2;
        int h = 18;
        for (int i = 3; i >= 1; i--) g.renderOutline(x - i, y - i, tw + i * 2, h + i * 2, a(0x3355C8FF, alpha * (1f - i / 4f)));
        g.fill(x, y, x + tw, y + h, a(0xF2081426, alpha));
        g.renderOutline(x, y, tw, h, a(EDGE, alpha));
        // боковые линии
        g.fill(x - 34, y + h / 2, x - 4, y + h / 2 + 1, a(EDGE, alpha * 0.8f));
        g.fill(x + tw + 4, y + h / 2, x + tw + 34, y + h / 2 + 1, a(EDGE, alpha * 0.8f));
        textCenter(g, f, text, cx, y + 4, 0xFFFFFFFF, alpha);
    }

    /** Разделитель, плавно гаснущий к краям. */
    public static void divider(GuiGraphics g, int x, int y, int w, float alpha) {
        int seg = 10;
        for (int i = 0; i < seg; i++) {
            float pos = (i + 0.5f) / seg;
            float fa = 1f - Math.abs(pos - 0.5f) * 2f;
            int sx = x + w * i / seg;
            int ex = x + w * (i + 1) / seg;
            g.fill(sx, y, ex, y + 1, a(EDGE, alpha * (0.15f + 0.85f * fa)));
        }
    }

    /** Полоса значения (HP / MP / опыт / мастерство) с бликом. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, double frac, int color, float alpha) {
        g.fill(x, y, x + w, y + h, a(0xB0000000, alpha));
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, a(0x6655C8FF, alpha));
        int fw = (int) Math.round(w * Mth.clamp(frac, 0, 1));
        if (fw > 0) {
            g.fill(x, y, x + fw, y + h, a(color, alpha));
            g.fill(x, y, x + fw, y + 1, a(0x60FFFFFF, alpha));
            g.fill(x + fw - 1, y, x + fw, y + h, a(0xFFFFFFFF, alpha));
        }
    }

    /** Кнопка в стиле Системы. Возвращает true при наведении. */
    public static boolean button(GuiGraphics g, Font f, Component label, int x, int y, int w, int h, int mx, int my, boolean enabled, float alpha) {
        boolean hover = enabled && inside(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, a(hover ? 0xD02A6FA8 : enabled ? 0xA0102C4A : 0x70101820, alpha));
        g.renderOutline(x, y, w, h, a(!enabled ? LOCK : hover ? 0xFFFFFFFF : EDGE, alpha));
        textCenter(g, f, label, x + w / 2, y + (h - 8) / 2, enabled ? INK : DIM, alpha);
        return hover;
    }

    /** Значок клавиши (Z / X / C / V). */
    public static void keyBadge(GuiGraphics g, Font f, Component key, int right, int y, boolean assigned, float alpha) {
        int w = Math.max(14, f.width(key) + 6);
        int x = right - w;
        if (assigned) g.fill(x, y, x + w, y + 11, a(EDGE, alpha));
        else g.renderOutline(x, y, w, 11, a(0x6655C8FF, alpha));
        textCenter(g, f, key, x + w / 2, y + 2, assigned ? 0xFF061424 : DIM, alpha);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static float easeOut(float t) {
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }
}
