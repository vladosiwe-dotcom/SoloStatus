package com.vladosiwe.solostatus.skill;

import net.minecraft.network.chat.Component;

public enum SkillCategory {
    ATTACK, MOVEMENT, DEFENSE, MAGIC, SUPPORT, PASSIVE, ULTIMATE;

    public Component displayName() {
        return Component.translatable("solostatus.category." + name().toLowerCase());
    }
}
