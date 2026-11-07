package com.vladosiwe.solostatus.stats;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.clazz.Specialization;
import com.vladosiwe.solostatus.compat.CompatManager;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Применяет характеристики, классовые и пассивные бонусы к ванильным атрибутам.
 * Модификаторы временные (transient): пересчитываются при входе, возрождении, смене измерения
 * и любом изменении профиля, поэтому не накапливаются и не портят сохранения.
 *
 * Урон, крит, уклонение и снижение урона применяются не через атрибуты, а в CombatEvents —
 * так они корректно работают с атаками Epic Fight, которые проходят через стандартные события урона.
 */
public final class AttributeSystem {
    private static final ResourceLocation HEALTH = SoloStatus.id("health");
    private static final ResourceLocation SPEED = SoloStatus.id("move_speed");
    private static final ResourceLocation ATK_SPEED = SoloStatus.id("attack_speed");
    private static final ResourceLocation LUCK = SoloStatus.id("luck");
    private static final ResourceLocation ARMOR = SoloStatus.id("armor");
    private static final ResourceLocation KNOCKBACK = SoloStatus.id("knockback");
    private static final ResourceLocation SAFE_FALL = SoloStatus.id("safe_fall");

    private AttributeSystem() {}

    public static void apply(ServerPlayer player) {
        PlayerData d = Hunter.get(player);
        HunterClass hc = d.hunterClass;
        Specialization spec = d.specialization;

        double health = StatFormulas.bonusHealth(d) + d.skillRank("warrior_spirit");
        if (d.achievements.contains("undying")) health += 2;

        double speed = StatFormulas.moveSpeed(d);
        if (hc == HunterClass.THIEF) speed += 0.05;
        if (spec == Specialization.RANGER) speed += d.skillRank("fleet_foot") * 0.03;
        if (d.achievements.contains("marathon")) speed += 0.03;

        double armor = d.skillRank("iron_skin");
        if (d.achievements.contains("iron_wall")) armor += 1;
        if (hc == HunterClass.DEFENDER) armor += 2;

        double knockback = d.skillRank("iron_skin") * 0.02 + d.skillRank("thorns_body") * 0.05;
        if (hc == HunterClass.DEFENDER) knockback += 0.1;

        set(player, Attributes.MAX_HEALTH, HEALTH, health, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MOVEMENT_SPEED, SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.ATTACK_SPEED, ATK_SPEED, StatFormulas.attackSpeed(d) + (d.achievements.contains("frenzy") ? 0.03 : 0),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.LUCK, LUCK, StatFormulas.luckAttribute(d), AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ARMOR, ARMOR, armor, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, Math.min(0.5, knockback), AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL, StatFormulas.safeFall(d), AttributeModifier.Operation.ADD_VALUE);

        CompatManager.applyMagicAttributes(player, d);

        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        float maxEnergy = (float) StatFormulas.maxEnergy(d);
        if (d.energy > maxEnergy) d.energy = maxEnergy;
    }

    /** Ставит/обновляет временный модификатор; нулевое значение удаляет его. */
    public static void set(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double amount,
                           AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;
        if (Math.abs(amount) < 1.0e-6) {
            inst.removeModifier(id);
        } else {
            inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }
}
