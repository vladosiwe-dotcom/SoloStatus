package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.data.PlayerData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Контекст применения активного навыка (только сервер). */
public record SkillContext(ServerPlayer player, ServerLevel level, PlayerData data, SkillDef def, int rank) {
    /** Линейное масштабирование от ранга: base + perRank * (rank - 1). */
    public double scale(double base, double perRank) {
        return base + perRank * (rank - 1);
    }
}
