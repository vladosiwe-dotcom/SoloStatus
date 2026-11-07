package com.vladosiwe.solostatus.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Отрисовка в стиле голографического системного окна: полупрозрачный тёмно-синий фон,
 * светящиеся голубые рамки, уголки-скобки и линии сканирования. Без внешних текстур.
 */
public final class Holo {
    public static final int BG = 0xD0061424;
    public static final int BG_LIGHT = 0xB00C2440;
    public static final int BORDER = 0xFF3FB4FF;
    public static final int GLOW = 0x553FB4FF;
    public static final int TEXT = 0xFFE6F4FF;
    public static final int DIM = 0xFF7FA6C8;
    public static final int ACCENT = 0xFF7FDBFF;
    public static final int GOLD = 0xFFFFD36B;
    public static final int RED = 0xFFFF6B6B;
    public static final int GREEN = 0xFF7DFF9B;
    public static final int LOCKED = 0xFF4A5A6A;
    public static final int PURPLE = 0xFFC08CFF;

    private Holo() {}

    public static int alpha(int color, float a) {
        int base = (color >>> 24) & 0xFF;
        int na = Mth.clamp((int) (base * a), 0, 255);
        return (na << 24) | (color & 0xFFFFFF);
    }

    /** Основная панель окна. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, float a) {
        g.fill(x, y, x + w, y + h, alpha(BG, a));
        g.fillGradient(x, y, x + w, y + 24, alpha(0x403FB4FF, a), alpha(0x003FB4FF, a));
        frame(g, x, y, w, h, alpha(BORDER, a));
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, alpha(GLOW, a));
        // линии сканирования
        long t = System.currentTimeMillis();
        int scan = (int) ((t / 30) % Math.max(1, h));
        g.fill(x + 1, y + scan, x + w - 1, y + scan + 1, alpha(0x203FB4FF, a));
        corners(g, x - 3, y - 3, w + 6, h + 6, alpha(ACCENT, a));
    }

    /** Внутренний блок. */
    public static void box(GuiGraphics g, int x, int y, int w, int h, int border) {
        g.fill(x, y, x + w, y + h, BG_LIGHT);
        g.renderOutline(x, y, w, h, border);
    }

    public static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.renderOutline(x, y, w, h, color);
    }

    public static void corners(GuiGraphics g, int x, int y, int w, int h, int color) {
        int l = 8;
        g.fill(x, y, x + l, y + 1, color);
        g.fill(x, y, x + 1, y + l, color);
        g.fill(x + w - l, y, x + w, y + 1, color);
        g.fill(x + w - 1, y, x + w, y + l, color);
        g.fill(x, y + h - 1, x + l, y + h, color);
        g.fill(x, y + h - l, x + 1, y + h, color);
        g.fill(x + w - l, y + h - 1, x + w, y + h, color);
        g.fill(x + w - 1, y + h - l, x + w, y + h, color);
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, double frac, int color) {
        frac = Mth.clamp(frac, 0, 1);
        g.fill(x, y, x + w, y + h, 0xA0000000);
        int fw = (int) Math.round(w * frac);
        if (fw > 0) {
            g.fillGradient(x, y, x + fw, y + h, color, alpha(color, 0.6f));
            g.fill(x + fw - 1, y, x + fw, y + h, 0xFFFFFFFF);
        }
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0x603FB4FF);
    }

    public static boolean button(GuiGraphics g, Font font, int x, int y, int w, int h, Component text, int mx, int my, boolean enabled) {
        boolean hover = enabled && inside(mx, my, x, y, w, h);
        int border = !enabled ? LOCKED : hover ? 0xFFFFFFFF : BORDER;
        g.fill(x, y, x + w, y + h, hover ? 0xC02A6FA8 : enabled ? 0xA0103050 : 0x80101820);
        g.renderOutline(x, y, w, h, border);
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, enabled ? TEXT : DIM);
        return hover;
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Текст с переносом строк. Возвращает высоту. */
    public static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int yy = y;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x, yy, color, false);
            yy += 10;
        }
        return yy - y;
    }

    public static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4f));
    }

    public static void chime() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.8f));
    }

    public static String pct(double v) {
        return (v >= 0 ? "+" : "") + Math.round(v * 1000) / 10.0 + "%";
    }
}
