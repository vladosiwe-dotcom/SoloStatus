package com.vladosiwe.solostatus.level;

/** Источник опыта системы — показывается игроку во всплывающей подписи «+N опыта». */
public enum XpReason {
    KILL, BOSS, DISCOVERY, ACHIEVEMENT, MASTERY, TRIAL, RANK, COMMAND;

    public static XpReason byIndex(int i) {
        return i >= 0 && i < values().length ? values()[i] : KILL;
    }

    public String key() {
        return "solostatus.xp." + name().toLowerCase();
    }
}
