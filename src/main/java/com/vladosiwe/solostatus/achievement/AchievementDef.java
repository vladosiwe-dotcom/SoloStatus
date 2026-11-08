package com.vladosiwe.solostatus.achievement;

import com.vladosiwe.solostatus.skill.Requirement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Достижение охотника.
 *
 * @param hidden      скрытое: не видно до выполнения
 * @param xp          опыт системы
 * @param sp          очки навыков (только за значимые вехи)
 * @param orbs        Сферы перерождения
 * @param title       открывает титул с тем же названием (эффект — solostatus.achievement.&lt;id&gt;.title)
 * @param rewardSkill редкий навык, который открывается этим достижением (сам навык требует это достижение)
 */
public record AchievementDef(String id, boolean hidden, Item icon, List<Requirement> requirements, int xp, int sp, int orbs,
                             boolean title, @Nullable String rewardSkill) {
    public Component name() {
        return Component.translatable("solostatus.achievement." + id);
    }

    public Component description() {
        return Component.translatable("solostatus.achievement." + id + ".desc");
    }

    /** Эффект титула (только для title = true). */
    public Component titleEffect() {
        return Component.translatable("solostatus.achievement." + id + ".title");
    }
}
