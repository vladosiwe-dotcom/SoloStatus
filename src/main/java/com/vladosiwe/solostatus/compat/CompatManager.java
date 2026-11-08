package com.vladosiwe.solostatus.compat;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.compat.epicfight.EpicFightCompat;
import com.vladosiwe.solostatus.compat.ironsspells.IronsSpellsCompat;
import com.vladosiwe.solostatus.data.PlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Точка входа всех интеграций. Классы интеграций обращаются к чужим модам только по id реестров
 * и через отражение с проверкой наличия, поэтому мод безопасно работает без них.
 */
public final class CompatManager {
    private static boolean irons;
    private static boolean epicFight;

    private CompatManager() {}

    public static void init() {
        irons = ModList.get().isLoaded("irons_spellbooks");
        epicFight = ModList.get().isLoaded("epicfight");
        if (ModList.get().isLoaded("bettercombat") && epicFight) {
            SoloStatus.LOGGER.warn("Обнаружены одновременно Epic Fight и Better Combat. Они управляют атаками по-разному; рекомендуется оставить только Epic Fight.");
        }
        if (irons) IronsSpellsCompat.init();
        if (epicFight) EpicFightCompat.init();
        SoloStatus.LOGGER.info("Solo Status System: Iron's Spells — {}, Epic Fight — {}", irons ? "есть" : "нет", epicFight ? "есть" : "нет");
    }

    public static boolean ironsLoaded() {
        return irons;
    }

    public static boolean epicFightLoaded() {
        return epicFight;
    }

    /** Анимация Epic Fight для навыка (только визуальная). false — Epic Fight нет или анимация не задана. */
    public static boolean playSkillAnimation(ServerPlayer player, String skillId) {
        return epicFight && EpicFightCompat.playAnimation(player, com.vladosiwe.solostatus.skill.SkillAnimations.forSkill(skillId));
    }

    public static void applyMagicAttributes(ServerPlayer player, PlayerData d) {
        if (irons) IronsSpellsCompat.applyAttributes(player, d);
    }

    /** Школа магии Iron's Spells для источника урона (например "fire") или null. */
    @Nullable
    public static String ironsSchool(DamageSource source) {
        return irons ? IronsSpellsCompat.school(source) : null;
    }
}
