package com.vladosiwe.solostatus.client.ui;

import com.vladosiwe.solostatus.SoloStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/** Шрифты окна Системы (Exo 2, OFL): обычный, жирный и крупный для цифр/заголовков. */
public final class SystemFonts {
    public static final ResourceLocation BODY = SoloStatus.id("system");
    public static final ResourceLocation BOLD = SoloStatus.id("system_bold");
    public static final ResourceLocation TITLE = SoloStatus.id("system_title");

    private SystemFonts() {}

    public static MutableComponent body(Component c) {
        return c.copy().withStyle(s -> s.withFont(BODY));
    }

    public static MutableComponent bold(Component c) {
        return c.copy().withStyle(s -> s.withFont(BOLD));
    }

    public static MutableComponent title(Component c) {
        return c.copy().withStyle(s -> s.withFont(TITLE));
    }

    public static MutableComponent body(String s) {
        return body(Component.literal(s));
    }

    public static MutableComponent bold(String s) {
        return bold(Component.literal(s));
    }

    public static MutableComponent title(String s) {
        return title(Component.literal(s));
    }

    public static MutableComponent tr(String key, Object... args) {
        return body(Component.translatable(key, args));
    }

    public static MutableComponent trBold(String key, Object... args) {
        return bold(Component.translatable(key, args));
    }
}
