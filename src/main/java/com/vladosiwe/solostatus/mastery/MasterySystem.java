package com.vladosiwe.solostatus.mastery;

import com.vladosiwe.solostatus.combat.WeaponType;
import com.vladosiwe.solostatus.config.SoloConfig;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.network.Notices;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Мастерство оружия и школ магии. Растёт только от реальных боевых действий (см. CombatEvents):
 * попадания по противникам с ограничением на одну цель, убийства с учётом опасности и анти-фарма,
 * блоки щитом, магический урон. Уровни 0..50; вехи 10/25/40/50 открывают навыки и бонусы.
 */
public final class MasterySystem {
    public static final double MAGIC_PACE = 0.75;

    private MasterySystem() {}

    public static int level(PlayerData d, WeaponType type) {
        return MasteryMath.level(d.masteryXp(type.key));
    }

    public static void add(ServerPlayer player, PlayerData d, String key, double amount, Component name) {
        if (amount <= 0) return;
        double before = d.masteryXp(key);
        int oldLevel = MasteryMath.level(before);
        if (oldLevel >= MasteryMath.MAX_LEVEL) return;
        double after = before + amount * SoloConfig.MASTERY_MULTIPLIER.get();
        d.mastery.put(key, after);
        int newLevel = MasteryMath.level(after);
        d.unlockCheckPending = true;
        d.softDirty = true;
        if (newLevel > oldLevel) {
            d.markDirty();
            boolean milestone = false;
            for (int l = oldLevel + 1; l <= newLevel; l++) { // a big gain can cross several levels: no milestone is skipped
                int bonus = switch (l) { case 10 -> 300; case 25 -> 1500; case 40 -> 4000; case 50 -> 8000; default -> 0; };
                if (bonus > 0) {
                    milestone = true;
                    com.vladosiwe.solostatus.level.LevelSystem.grantXp(player, bonus, com.vladosiwe.solostatus.level.XpReason.MASTERY);
                }
            }
            if (milestone || newLevel % 5 == 0) {
                Notices.send(player, milestone ? Notices.ACHIEVEMENT : Notices.INFO,
                        Component.translatable("solostatus.notice.mastery_up", name, newLevel),
                        Component.translatable(milestone && (newLevel == 10 || newLevel == 25 || newLevel == 40 || newLevel == 50)
                                ? "solostatus.notice.mastery_milestone." + newLevel : "solostatus.notice.mastery_up.desc"),
                        key.startsWith("magic:") ? net.minecraft.world.item.Items.ENCHANTED_BOOK : WeaponType.byKey(key).icon);
            }
        }
    }

    public static void addWeapon(ServerPlayer player, PlayerData d, WeaponType type, double amount) {
        if (type == null || !type.hasMastery()) return;
        add(player, d, type.key, amount, type.displayName());
    }

    /** Магическое мастерство по школе (school — путь id школы Iron's Spells или "arcane" для навыков системы). */
    public static void addMagic(ServerPlayer player, PlayerData d, String school, double amount) {
        // magic is the harder path: schools grow at 75% of a weapon's pace
        add(player, d, "magic:" + school, amount * MAGIC_PACE, schoolName(school));
    }

    public static Component schoolName(String school) {
        return Component.translatableWithFallback("solostatus.school." + school, capitalize(school));
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' ');
    }
}
