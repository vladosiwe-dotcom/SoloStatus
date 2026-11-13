package com.vladosiwe.solostatus.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Клавиши мода. Все переназначаются в Настройки → Управление → «Solo Status System». */
public final class Keys {
    public static final String CATEGORY = "key.categories.solostatus";

    public static final KeyMapping OPEN_STATUS = new KeyMapping("key.solostatus.status", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY); // not bound: the System is called by voice; a key can be set in Controls
    public static final KeyMapping INSPECT = new KeyMapping("key.solostatus.inspect", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY);
    /** Голос: удерживать и говорить (режим PUSH_TO_TALK). */
    public static final KeyMapping VOICE = new KeyMapping("key.solostatus.voice", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
    public static final KeyMapping[] SKILLS = {
            new KeyMapping("key.solostatus.skill1", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY),
            new KeyMapping("key.solostatus.skill2", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY),
            new KeyMapping("key.solostatus.skill3", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY),
            new KeyMapping("key.solostatus.skill4", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY),
    };

    private Keys() {}
}
