package com.vladosiwe.solostatus.compat.epicfight;

import com.vladosiwe.solostatus.SoloStatus;

/**
 * Интеграция с Epic Fight.
 *
 * Что работает уже сейчас (без compile-зависимости):
 *  - атаки Epic Fight наносят урон через стандартные события урона NeoForge, поэтому к ним применяются
 *    STR/PER/крит/мастерство/качество оружия/пассивки — Solo Status не создаёт вторую боевую систему;
 *  - AGI повышает атрибут скорости атаки, от которого Epic Fight масштабирует скорость анимаций атак;
 *  - блоки щитом Epic Fight, если они проходят через ванильный механизм щита, засчитываются как блоки.
 *
 * Что требует API-jar Epic Fight для вашей версии (см. docs/INTEGRATIONS.md):
 *  - проигрывание анимаций Epic Fight при применении активных навыков;
 *  - засчитывание уклонений (dodge) и парирований (guard/parry) Epic Fight в счётчики пробуждения;
 *  - регистрация навыков системы как Skill Epic Fight.
 * Эти части намеренно не реализованы «вслепую»: без проверки API они бы стали фиктивными методами.
 */
public final class EpicFightCompat {
    private EpicFightCompat() {}

    public static void init() {
        SoloStatus.LOGGER.info("Epic Fight обнаружен: бонусы системы применяются к атакам Epic Fight через события урона. " +
                "Анимации навыков и счётчики парирований/уклонений Epic Fight подключаются отдельным модулем (docs/INTEGRATIONS.md).");
    }
}
