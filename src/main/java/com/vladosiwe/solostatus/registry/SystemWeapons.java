package com.vladosiwe.solostatus.registry;

import com.vladosiwe.solostatus.combat.WeaponType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The System's own weapons: one of every weapon type in two grades. Hunter grade is forged from iron with an
 * amethyst core; Shadow grade is a netherite upgrade of it in the smithing table. Each weapon is tagged with its type
 * (data/solostatus/tags/item/weapons/*), so mastery, quality, class proficiency and skills treat it as that type,
 * and Epic Fight gets a moveset for it from data/solostatus/capabilities/weapons/*.json. The staff carries Iron's
 * Spells spell power and mana.
 */
public final class SystemWeapons {

    public enum Grade implements Tier {
        HUNTER("hunter", 750, 1.0f, 12, Rarity.UNCOMMON, Items.IRON_INGOT),
        SHADOW("shadow", 2400, 1.45f, 16, Rarity.EPIC, Items.NETHERITE_INGOT);

        public final String key;
        private final int uses;
        /** Multiplier of the base damage of each kind. */
        public final float power;
        private final int enchant;
        public final Rarity rarity;
        private final Item repair;

        Grade(String key, int uses, float power, int enchant, Rarity rarity, Item repair) {
            this.key = key;
            this.uses = uses;
            this.power = power;
            this.enchant = enchant;
            this.rarity = rarity;
            this.repair = repair;
        }

        @Override public int getUses() { return uses; }
        @Override public float getSpeed() { return this == SHADOW ? 9f : 6f; }
        @Override public float getAttackDamageBonus() { return 0; } // damage is set per weapon below
        @Override public TagKey<Block> getIncorrectBlocksForDrops() {
            return this == SHADOW ? BlockTags.INCORRECT_FOR_NETHERITE_TOOL : BlockTags.INCORRECT_FOR_IRON_TOOL;
        }
        @Override public int getEnchantmentValue() { return enchant; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(repair); }
    }

    /**
     * A kind of System weapon: its weapon type, its name in ids, damage at Hunter grade (hearts × 2, including the
     * hand's 1) and attack speed (attacks per second).
     */
    public enum Kind {
        SWORD(WeaponType.SWORD, "sword", 6.5f, 1.6f),
        GREATSWORD(WeaponType.GREATSWORD, "greatsword", 9.5f, 0.9f),
        DAGGER(WeaponType.DAGGER, "dagger", 4.5f, 2.4f),
        BATTLEAXE(WeaponType.AXE, "battleaxe", 8.5f, 1.0f),
        WARHAMMER(WeaponType.MACE, "warhammer", 10f, 0.8f),
        SPEAR(WeaponType.SPEAR, "spear", 7f, 1.2f),
        STAFF(WeaponType.STAFF, "staff", 4f, 1.3f);

        public final WeaponType type;
        public final String key;
        public final float damage;
        public final float speed;

        Kind(WeaponType type, String key, float damage, float speed) {
            this.type = type;
            this.key = key;
            this.damage = damage;
            this.speed = speed;
        }
    }

    private static final Map<Grade, Map<Kind, DeferredItem<SwordItem>>> ITEMS = new EnumMap<>(Grade.class);

    static {
        for (Grade g : Grade.values()) {
            Map<Kind, DeferredItem<SwordItem>> row = new EnumMap<>(Kind.class);
            for (Kind k : Kind.values()) {
                row.put(k, ModItems.ITEMS.register(g.key + "_" + k.key, () -> new SwordItem(g, properties(g, k))));
            }
            ITEMS.put(g, row);
        }
    }

    private SystemWeapons() {}

    /** Called from ModItems so these are registered with the other items. */
    public static void init() {}

    public static SwordItem get(Grade g, Kind k) {
        return ITEMS.get(g).get(k).get();
    }

    public static List<Item> all() {
        List<Item> out = new ArrayList<>();
        for (Grade g : Grade.values()) for (Kind k : Kind.values()) out.add(get(g, k));
        return out;
    }

    private static Item.Properties properties(Grade g, Kind k) {
        Item.Properties p = new Item.Properties().rarity(g.rarity).attributes(attributes(g, k));
        if (g == Grade.SHADOW) p = p.fireResistant();
        return p;
    }

    private static ItemAttributeModifiers attributes(Grade g, Kind k) {
        var b = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, k.damage * g.power - 1, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, k.speed - 4, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
        if (k == Kind.SPEAR) { // a longer reach
            b.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(id(g, k, "reach"), 1.0, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        if (k == Kind.STAFF) { // a caster's focus: Iron's Spells power and mana
            irons(b, g, k, "spell_power", g == Grade.SHADOW ? 0.2 : 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            irons(b, g, k, "max_mana", g == Grade.SHADOW ? 75 : 35, AttributeModifier.Operation.ADD_VALUE);
        }
        return b.build();
    }

    private static void irons(ItemAttributeModifiers.Builder b, Grade g, Kind k, String attribute, double value, AttributeModifier.Operation op) {
        BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", attribute))
                .ifPresent(h -> b.add(h, new AttributeModifier(id(g, k, attribute), value, op), EquipmentSlotGroup.MAINHAND));
    }

    private static ResourceLocation id(Grade g, Kind k, String what) {
        return com.vladosiwe.solostatus.SoloStatus.id(g.key + "_" + k.key + "_" + what);
    }
}
