package com.vladosiwe.solostatus.skill;

/** Реализация активного навыка. Возвращает false, если навык не сработал (нет цели) — тогда ресурсы не тратятся. */
@FunctionalInterface
public interface SkillEffect {
    boolean execute(SkillContext ctx);
}
