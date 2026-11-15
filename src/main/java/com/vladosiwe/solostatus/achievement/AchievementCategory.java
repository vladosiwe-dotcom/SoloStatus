package com.vladosiwe.solostatus.achievement;

import net.minecraft.network.chat.Component;

/** Sections of the achievement list (solostatus.achcat.&lt;key&gt;). */
public enum AchievementCategory {
    PATH("path"),
    CLASS("class"),
    COMBAT("combat"),
    BOSSES("bosses"),
    MASTERY("mastery"),
    HUNT("hunt"),
    HIDDEN("hidden"),
    LEGENDARY("legendary");

    public static final AchievementCategory[] VALUES = values();

    public final String key;

    AchievementCategory(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("solostatus.achcat." + key);
    }
}
