package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.combat.WeaponType;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.mastery.MasteryMath;
import com.vladosiwe.solostatus.stats.Stat;
import net.minecraft.network.chat.Component;

/** Фабрика типовых требований. */
public final class Req {
    private Req() {}

    private static double ratio(double have, double need) {
        return need <= 0 ? 1 : Math.min(1, have / need);
    }

    public static Requirement level(int n) {
        return new Requirement() {
            public double progress(PlayerData d) { return ratio(d.level, n); }
            public Component describe(PlayerData d) { return Component.translatable("solostatus.req.level", n, d.level); }
        };
    }

    public static Requirement stat(Stat s, int n) {
        return new Requirement() {
            public double progress(PlayerData d) { return ratio(d.stat(s), n); }
            public Component describe(PlayerData d) { return Component.translatable("solostatus.req.stat", s.shortName(), n, d.stat(s)); }
        };
    }

    public static Requirement skill(String id, int rank) {
        return new Requirement() {
            public double progress(PlayerData d) { return ratio(d.skillRank(id), rank); }
            public Component describe(PlayerData d) {
                return Component.translatable("solostatus.req.skill", Component.translatable("solostatus.skill." + id), rank);
            }
        };
    }

    public static Requirement mastery(WeaponType type, int lvl) {
        return masteryKey(type.key, type.displayName(), lvl);
    }

    public static Requirement masteryKey(String key, Component name, int lvl) {
        return new Requirement() {
            public double progress(PlayerData d) { return ratio(MasteryMath.level(d.masteryXp(key)), lvl); }
            public Component describe(PlayerData d) {
                return Component.translatable("solostatus.req.mastery", name, lvl, MasteryMath.level(d.masteryXp(key)));
            }
        };
    }

    /** Боевой счётчик (kills, blocks, dodges...). Ключ описания: solostatus.counter.&lt;key&gt;. */
    public static Requirement counter(String key, long n) {
        return new Requirement() {
            public double progress(PlayerData d) { return ratio(d.counter(key), n); }
            public Component describe(PlayerData d) {
                return Component.translatable("solostatus.req.counter", Component.translatable("solostatus.counter." + key), n, d.counter(key));
            }
        };
    }

    public static Requirement distinctKills(int n) {
        return new Requirement() {
            public double progress(PlayerData d) { return ratio(d.killsByType.size(), n); }
            public Component describe(PlayerData d) { return Component.translatable("solostatus.req.distinct", n, d.killsByType.size()); }
        };
    }

    public static Requirement bosses(int n) {
        return new Requirement() {
            public double progress(PlayerData d) { return ratio(d.bossesKilled.size(), n); }
            public Component describe(PlayerData d) { return Component.translatable("solostatus.req.bosses", n, d.bossesKilled.size()); }
        };
    }

    public static Requirement achievement(String id) {
        return new Requirement() {
            public double progress(PlayerData d) { return d.achievements.contains(id) ? 1 : 0; }
            public Component describe(PlayerData d) {
                return Component.translatable("solostatus.req.achievement", Component.translatable("solostatus.achievement." + id));
            }
        };
    }

    public static Requirement advanced() {
        return new Requirement() {
            public double progress(PlayerData d) { return d.advanced ? 1 : 0; }
            public Component describe(PlayerData d) { return Component.translatable("solostatus.req.advanced"); }
        };
    }
}
