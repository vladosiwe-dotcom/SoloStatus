package com.vladosiwe.solostatus.quality;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.combat.WeaponClassifier;
import com.vladosiwe.solostatus.combat.WeaponType;
import com.vladosiwe.solostatus.config.SoloConfig;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.registry.ModComponents;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Индивидуальное качество оружия.
 * Источники: крафт (в основном Common), мобы (часто повреждённое), сундуки (Common–Rare), боссы (Epic–Legendary).
 * Параметры хранятся в data component и применяются через ItemAttributeModifierEvent — без замены предметов.
 */
@EventBusSubscriber(modid = SoloStatus.MODID)
public final class QualitySystem {
    private static final ResourceLocation DAMAGE_ID = SoloStatus.id("quality_damage");
    private static final ResourceLocation SPEED_ID = SoloStatus.id("quality_speed");
    private static final ResourceLocation SHIELD_ID = SoloStatus.id("quality_shield");

    public enum Source {
        //                Common Uncommon Rare Epic Legendary
        CRAFTED(new int[]{85, 13, 2, 0, 0}, 0.0),
        MOB(new int[]{80, 16, 4, 0, 0}, 0.7),
        CHEST(new int[]{55, 30, 15, 0, 0}, 0.0),
        BOSS(new int[]{0, 0, 0, 80, 20}, 0.0);

        final int[] weights;
        final double damagedChance;

        Source(int[] weights, double damagedChance) {
            this.weights = weights;
            this.damagedChance = damagedChance;
        }
    }

    private QualitySystem() {}

    @Nullable
    public static WeaponQuality get(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(ModComponents.QUALITY.get());
    }

    public static boolean enabled() {
        try {
            return SoloConfig.WEAPON_QUALITY_ENABLED.get();
        } catch (Exception e) {
            return true;
        }
    }

    /** Случайный бросок качества. luckBonus (0..0.25) смещает вероятности к более высоким редкостям. */
    public static WeaponQuality roll(ItemStack stack, Source source, RandomSource random, double luckBonus) {
        double[] w = new double[QualityRarity.VALUES.length];
        double total = 0;
        for (int i = 0; i < w.length; i++) {
            w[i] = source.weights[i] * (i == 0 ? 1 : 1 + luckBonus * 4 * i);
            total += w[i];
        }
        double r = random.nextDouble() * total;
        int tier = 0;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r <= 0) { tier = i; break; }
        }
        QualityRarity rarity = QualityRarity.byIndex(tier);
        float damage = rarity.minDamage + random.nextFloat() * (rarity.maxDamage - rarity.minDamage);
        float speed = tier >= 1 ? -0.03f + random.nextFloat() * (0.05f + 0.02f * tier) : 0f;
        float crit = tier >= 2 ? random.nextFloat() * (0.02f * tier + (tier == 4 ? 0.02f : 0)) : 0f;
        int condition = random.nextDouble() < source.damagedChance ? WeaponQuality.DAMAGED : WeaponQuality.PRISTINE;
        WeaponQuality q = new WeaponQuality(tier, round(damage), round(speed), round(crit), condition, false);
        apply(stack, q);
        if (condition == WeaponQuality.DAMAGED && stack.isDamageableItem()) {
            stack.setDamageValue((int) (stack.getMaxDamage() * (0.4 + random.nextDouble() * 0.4)));
        }
        return q;
    }

    private static float round(float v) {
        return Math.round(v * 1000f) / 1000f;
    }

    public static void apply(ItemStack stack, WeaponQuality q) {
        stack.set(ModComponents.QUALITY.get(), q);
        if (q.tier() != QualityRarity.COMMON) stack.set(DataComponents.RARITY, q.tier().vanilla);
    }

    /** Оружие с босса: случайное ванильное оружие высокого уровня качества Epic/Legendary. */
    public static ItemStack bossWeapon(RandomSource random, double luck) {
        Item[] pool = {Items.DIAMOND_SWORD, Items.NETHERITE_SWORD, Items.DIAMOND_AXE, Items.NETHERITE_AXE, Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.MACE};
        ItemStack stack = new ItemStack(pool[random.nextInt(pool.length)]);
        roll(stack, Source.BOSS, random, luck);
        return stack;
    }

    // ============================== События ==============================

    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        WeaponQuality q = get(stack);
        if (q == null) return;
        WeaponType type = WeaponClassifier.classify(stack);
        if (type == WeaponType.SHIELD) {
            event.addModifier(Attributes.ARMOR, new AttributeModifier(SHIELD_ID, q.rarity() * 0.5 + (q.broken() ? -1 : 0), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND);
            return;
        }
        if (type.ranged() || type == WeaponType.STAFF) return; // урон луков считается в CombatEvents, посохов — магией
        float dmg = q.effectiveDamageBonus();
        if (dmg != 0) event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(DAMAGE_ID, dmg, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.MAINHAND);
        if (q.speed() != 0) event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(SPEED_ID, q.speed(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.MAINHAND);
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer p)) return;
        ItemStack stack = event.getCrafting();
        if (WeaponClassifier.isQualityWeapon(stack) && get(stack) == null) {
            roll(stack, Source.CRAFTED, p.getRandom(), StatFormulas.qualityBonus(Hunter.get(p)));
        }
    }

    /** Сломанное оружие нельзя отремонтировать на наковальне. */
    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        WeaponQuality q = get(event.getLeft());
        if (q != null && q.broken()) event.setCanceled(true);
    }

    /**
     * Вызывается из тика игрока раз в 2 секунды: выдаёт качество оружию без него (сделки, команды, другие моды)
     * и помечает почти полностью изношенное оружие как сломанное.
     */
    public static void tickHeld(ServerPlayer p) {
        if (!enabled()) return;
        ItemStack stack = p.getMainHandItem();
        if (!WeaponClassifier.isQualityWeapon(stack)) return;
        WeaponQuality q = get(stack);
        if (q == null) {
            roll(stack, Source.CRAFTED, p.getRandom(), StatFormulas.qualityBonus(Hunter.get(p)));
            return;
        }
        if (!q.broken() && stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() * 0.95) {
            apply(stack, q.withBroken());
            com.vladosiwe.solostatus.network.Notices.warn(p, "solostatus.notice.weapon_broken");
        }
    }
}
