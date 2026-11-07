package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.clazz.ClassSystem;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.network.Notices;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Адаптивное пробуждение навыков. Проверка событийная: боевые события только ставят флаг
 * unlockCheckPending, а сама проверка выполняется не чаще раза в секунду на игрока.
 */
public final class SkillUnlockSystem {
    private SkillUnlockSystem() {}

    public static void check(ServerPlayer player) {
        PlayerData d = Hunter.get(player);
        if (!d.unlockCheckPending || !d.hasClass()) return;
        d.unlockCheckPending = false;

        ClassSystem.checkTrial(player, d);

        for (SkillDef def : Skills.all()) {
            if (!def.source.autoAwaken() || d.hasSkill(def.id)) continue;
            if (def.requirementsMet(d)) awaken(player, d, def);
        }
    }

    /** Пробуждение — отдельное игровое событие: частицы, звук и большое системное окно у игрока. */
    public static void awaken(ServerPlayer player, PlayerData d, SkillDef def) {
        SkillSystem.grant(player, d, def);
        ServerLevel level = player.serverLevel();
        double x = player.getX(), y = player.getY(), z = player.getZ();
        level.sendParticles(SkillFx.SYSTEM_DUST, x, y + 1, z, 80, 0.8, 1.2, 0.8, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 1, z, 40, 0.3, 1.5, 0.3, 0.12);
        level.sendParticles(ParticleTypes.ENCHANT, x, y + 2, z, 80, 1, 1, 1, 1.0);
        level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.5f);
        level.playSound(null, x, y, z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.2f);
        Component title = Component.translatable(def.source == SkillSource.HIDDEN ? "solostatus.notice.hidden_skill" : "solostatus.notice.awakening");
        Notices.send(player, Notices.AWAKENING, title, def.name());
    }
}
