package com.vladosiwe.solostatus.skill;

/** Откуда берётся навык. Определяет способ получения и стоимость. */
public enum SkillSource {
    /** Выдаётся бесплатно при выборе класса. */
    CLASS_START,
    /** Классовый навык: условия + очки навыков. */
    CLASS,
    /** Навык специализации: условия + очки навыков. */
    SPEC,
    /** Высшее умение класса — открывается Испытанием смены пути. */
    ULTIMATE,
    /** Пробуждается автоматически через боевой опыт, бесплатно. Доступно любому классу. */
    AWAKENING,
    /** Как AWAKENING, но название и условия скрыты до пробуждения. */
    HIDDEN;

    public boolean autoAwaken() {
        return this == AWAKENING || this == HIDDEN || this == ULTIMATE;
    }
}
