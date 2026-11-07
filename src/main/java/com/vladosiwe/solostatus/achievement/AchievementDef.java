package com.vladosiwe.solostatus.achievement;

import com.vladosiwe.solostatus.skill.Requirement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Достижение охотника.
 *
 * @param hidden скрытое: не видно в списке до выполнения, название и награда раскрываются уведомлением
 * @param xp     награда опытом
 * @param sp     награда очками навыков (только за значимые достижения)
 * @param orbs   Сферы перерождения
 * @param perk   есть ли постоянный пассивный бонус (описан в solostatus.achievement.&lt;id&gt;.perk)
 */
public record AchievementDef(String id, boolean hidden, Item icon, List<Requirement> requirements, int xp, int sp, int orbs, boolean perk) {
    public Component name() {
        return Component.translatable("solostatus.achievement." + id);
    }

    public Component description() {
        return Component.translatable("solostatus.achievement." + id + ".desc");
    }

    public Component perkText() {
        return Component.translatable("solostatus.achievement." + id + ".perk");
    }
}
