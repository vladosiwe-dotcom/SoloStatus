package com.vladosiwe.solostatus.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.skill.SkillDef;
import com.vladosiwe.solostatus.skill.SkillSource;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Минималистичные иконки навыков: белый знак 64×64 (textures/gui/skill/&lt;id&gt;.png, со сглаживанием),
 * который при отрисовке окрашивается в цвет происхождения навыка. Один цвет — одно происхождение,
 * поэтому по иконке сразу видно, откуда навык.
 */
public final class SkillIcons {
    public static final int CLASS = 0xFF55C8FF;      // классовые и стартовые
    public static final int SPEC = 0xFFFFD46B;       // специализация
    public static final int AWAKENING = 0xFF7DFFC8;  // пробуждения
    public static final int HIDDEN = 0xFFC79BFF;     // скрытые
    public static final int ULTIMATE = 0xFFFF8A5C;   // высшее умение (смена пути)
    public static final int LOCKED = 0xFF6F8399;     // ещё не изучен

    private SkillIcons() {}

    public static int color(SkillSource source) {
        return switch (source) {
            case CLASS_START, CLASS -> CLASS;
            case SPEC -> SPEC;
            case AWAKENING -> AWAKENING;
            case HIDDEN -> HIDDEN;
            case ULTIMATE -> ULTIMATE;
        };
    }

    public static ResourceLocation texture(SkillDef def) {
        return SoloStatus.id("textures/gui/skill/" + def.id + ".png");
    }

    /** Иконка навыка размером size×size в цвете argb (альфа цвета умножается на alpha). */
    public static void draw(GuiGraphics g, SkillDef def, int x, int y, int size, int argb, float alpha) {
        float a = ((argb >>> 24) & 0xFF) / 255f * alpha;
        if (a < 0.02f) return;
        RenderSystem.enableBlend();
        g.setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, a);
        g.blit(texture(def), x, y, size, size, 0f, 0f, 64, 64, 64, 64);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Иконка в цвете происхождения; не изученные навыки — приглушённые. */
    public static void draw(GuiGraphics g, SkillDef def, int x, int y, int size, boolean learned, float alpha) {
        draw(g, def, x, y, size, learned ? color(def.source) : LOCKED, alpha);
    }
}
