package com.vladosiwe.solostatus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Серверная конфигурация баланса (config/solostatus-server.toml в папке мира)
 * и клиентская конфигурация интерфейса.
 */
public final class SoloConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    // --- Прогрессия ---
    public static final ModConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ModConfigSpec.IntValue STAT_POINTS_PER_LEVEL;
    public static final ModConfigSpec.IntValue MAX_STAT_VALUE;
    public static final ModConfigSpec.IntValue STAT_SOFT_CAP;
    public static final ModConfigSpec.DoubleValue MASTERY_MULTIPLIER;
    public static final ModConfigSpec.IntValue SPECIALIZATION_LEVEL;
    public static final ModConfigSpec.IntValue ADVANCEMENT_LEVEL;

    // --- Характеристики (множители эффектов) ---
    public static final ModConfigSpec.DoubleValue STR_DAMAGE_PER_POINT;
    public static final ModConfigSpec.DoubleValue AGI_SPEED_PER_POINT;
    public static final ModConfigSpec.DoubleValue AGI_DODGE_PER_POINT;
    public static final ModConfigSpec.DoubleValue PER_PROJECTILE_PER_POINT;
    public static final ModConfigSpec.DoubleValue PER_CRIT_PER_POINT;
    public static final ModConfigSpec.DoubleValue VIT_HEALTH_PER_POINT;
    public static final ModConfigSpec.DoubleValue VIT_REDUCTION_PER_POINT;
    public static final ModConfigSpec.DoubleValue INT_MAGIC_PER_POINT;
    public static final ModConfigSpec.DoubleValue LUCK_DROP_PER_POINT;

    // --- Анти-фарм ---
    public static final ModConfigSpec.DoubleValue SPAWNER_FACTOR;
    public static final ModConfigSpec.DoubleValue PVP_PROGRESS_FACTOR;

    // --- Качество оружия ---
    public static final ModConfigSpec.BooleanValue WEAPON_QUALITY_ENABLED;
    public static final ModConfigSpec.DoubleValue BOSS_ORB_CHANCE;

    // --- Осмотр ---
    public static final ModConfigSpec.BooleanValue ALLOW_INSPECT;
    public static final ModConfigSpec.BooleanValue AWAKENING_INTRO;
    public static final ModConfigSpec.BooleanValue AWAKENING_EVERY_JOIN;
    public static final ModConfigSpec.IntValue INSPECT_RANGE;

    // --- Клиент ---
    public static final ModConfigSpec.BooleanValue CHAT_PHRASE_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> CHAT_PHRASE;
    public static final ModConfigSpec.BooleanValue SHOW_HUD;
    public static final ModConfigSpec.BooleanValue EPIC_FIGHT_FIRST_PERSON_BODY;
    public static final ModConfigSpec.EnumValue<com.vladosiwe.solostatus.voice.VoiceMode> VOICE_MODE;
    public static final ModConfigSpec.ConfigValue<String> VOICE_LANGUAGE;
    public static final ModConfigSpec.ConfigValue<String> VOICE_MODEL_PATH;
    public static final ModConfigSpec.BooleanValue VOICE_INDICATOR;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("progression");
        XP_MULTIPLIER = b.comment("Множитель опыта персонажа").defineInRange("xpMultiplier", 1.0, 0.0, 100.0);
        STAT_POINTS_PER_LEVEL = b.comment("Очков характеристик за уровень").defineInRange("statPointsPerLevel", 3, 0, 20);
        MAX_STAT_VALUE = b.comment("Жёсткий предел значения одной характеристики").defineInRange("maxStatValue", 150, 20, 1000);
        STAT_SOFT_CAP = b.comment("После этого числа вложенных очков эффект характеристики уменьшается вдвое").defineInRange("statSoftCap", 40, 1, 1000);
        MASTERY_MULTIPLIER = b.comment("Множитель прироста мастерства").defineInRange("masteryMultiplier", 1.0, 0.0, 100.0);
        SPECIALIZATION_LEVEL = b.comment("Уровень выбора специализации").defineInRange("specializationLevel", 5, 1, 100);
        ADVANCEMENT_LEVEL = b.comment("Уровень, с которого доступно Испытание смены пути (вторая ступень специализации)").defineInRange("advancementLevel", 40, 1, 100);
        b.pop();

        b.push("stats");
        STR_DAMAGE_PER_POINT = b.defineInRange("strPhysicalDamagePerPoint", 0.006, 0, 1);
        AGI_SPEED_PER_POINT = b.defineInRange("agiMoveSpeedPerPoint", 0.0025, 0, 1);
        AGI_DODGE_PER_POINT = b.defineInRange("agiDodgeChancePerPoint", 0.0015, 0, 1);
        PER_PROJECTILE_PER_POINT = b.defineInRange("perProjectileDamagePerPoint", 0.006, 0, 1);
        PER_CRIT_PER_POINT = b.defineInRange("perCritChancePerPoint", 0.002, 0, 1);
        VIT_HEALTH_PER_POINT = b.defineInRange("vitHealthPerPoint", 0.4, 0, 10);
        VIT_REDUCTION_PER_POINT = b.defineInRange("vitDamageReductionPerPoint", 0.0015, 0, 1);
        INT_MAGIC_PER_POINT = b.defineInRange("intMagicPowerPerPoint", 0.005, 0, 1);
        LUCK_DROP_PER_POINT = b.defineInRange("luckExtraDropPerPoint", 0.002, 0, 1);
        b.pop();

        b.push("antiFarm");
        SPAWNER_FACTOR = b.comment("Доля прогресса за мобов из обычных спавнеров").defineInRange("spawnerFactor", 0.25, 0, 1);
        PVP_PROGRESS_FACTOR = b.comment("Доля прогресса мастерства за бой с игроками").defineInRange("pvpFactor", 0.5, 0, 1);
        b.pop();

        b.push("weapons");
        WEAPON_QUALITY_ENABLED = b.define("weaponQualityEnabled", true);
        BOSS_ORB_CHANCE = b.comment("Шанс выпадения Сферы перерождения с босса").defineInRange("bossResetOrbChance", 0.25, 0, 1);
        b.pop();

        b.push("inspect");
        ALLOW_INSPECT = b.comment("Разрешить осмотр статуса других игроков").define("allowInspect", true);
        INSPECT_RANGE = b.defineInRange("inspectRange", 16, 2, 128);
        b.pop();

        b.push("awakening");
        AWAKENING_INTRO = b.comment("Сцена пробуждения при входе игрока на сервер (лёжа → открывает глаза → садится → встаёт).",
                "Сам игрок видит её от первого лица, остальные рядом — со стороны.")
                .define("introOnFirstJoin", true);
        AWAKENING_EVERY_JOIN = b.comment("true — при каждом входе на сервер, false — только при самом первом").define("everyJoin", true);
        b.pop();
        SPEC = b.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        CHAT_PHRASE_ENABLED = c.comment("Открывать окно статуса фразой в чате (текстовый вариант голосовой команды)").define("chatPhraseEnabled", true);
        CHAT_PHRASE = c.define("chatPhrase", "Система, покажи статус");
        SHOW_HUD = c.define("showHud", true);
        EPIC_FIGHT_FIRST_PERSON_BODY = c.comment("Epic Fight: показывать своё тело от первого лица (его опция first_person_model).",
                "false — обычные руки: модель с бронёй не торчит по краям экрана. Анимации Epic Fight видны со стороны и другим игрокам.")
                .define("epicFightFirstPersonBody", false);
        c.push("voice");
        VOICE_MODE = c.comment("Голосовые команды (Vosk, распознавание локально на вашем ПК, звук никуда не отправляется).",
                "OFF — выключено, PUSH_TO_TALK — только пока зажата клавиша голоса, ALWAYS — постоянно слушать командные фразы.")
                .defineEnum("mode", com.vladosiwe.solostatus.voice.VoiceMode.ALWAYS);
        VOICE_LANGUAGE = c.comment("Язык распознавания: auto (по языку игры), ru или en").define("language", "auto");
        VOICE_MODEL_PATH = c.comment("Путь к своей модели Vosk. Пусто — config/solostatus/vosk/<модель>").define("modelPath", "");
        VOICE_INDICATOR = c.comment("Показывать индикатор микрофона на экране").define("indicator", true);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private SoloConfig() {}
}
