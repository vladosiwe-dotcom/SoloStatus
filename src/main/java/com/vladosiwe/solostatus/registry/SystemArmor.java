package com.vladosiwe.solostatus.registry;

import com.vladosiwe.solostatus.SoloStatus;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The System's armour, three sets by weight: Vanguard (heavy, for Warriors and Defenders), Stalker (light: movement
 * and attack speed, for Thieves and Archers) and Arcanist robes (Iron's Spells mana and spell power, for Mages and
 * Supports). Plain vanilla armour items: Epic Fight gives them its usual armour handling (weight from defence), so
 * nothing special is needed for it. Textures are drawn by tools/icons/make_armor.py.
 */
public final class SystemArmor {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, SoloStatus.MODID);

    public enum Set {
        VANGUARD("vanguard", new int[]{3, 6, 8, 3}, 2.5f, 0.1f, 33, 12, Rarity.UNCOMMON, Items.IRON_INGOT),
        STALKER("stalker", new int[]{2, 4, 5, 2}, 0f, 0f, 22, 15, Rarity.UNCOMMON, Items.LEATHER),
        ARCANIST("arcanist", new int[]{1, 4, 5, 2}, 0f, 0f, 20, 20, Rarity.UNCOMMON, Items.AMETHYST_SHARD);

        public final String key;
        /** Defence of boots, leggings, chestplate, helmet. */
        final int[] defense;
        final float toughness, knockback;
        final int durability, enchant;
        final Rarity rarity;
        final Item repair;
        DeferredHolder<ArmorMaterial, ArmorMaterial> material;

        Set(String key, int[] defense, float toughness, float knockback, int durability, int enchant, Rarity rarity, Item repair) {
            this.key = key;
            this.defense = defense;
            this.toughness = toughness;
            this.knockback = knockback;
            this.durability = durability;
            this.enchant = enchant;
            this.rarity = rarity;
            this.repair = repair;
        }
    }

    private static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
    private static final Map<Set, Map<ArmorItem.Type, DeferredItem<ArmorItem>>> ITEMS = new EnumMap<>(Set.class);

    static {
        for (Set s : Set.values()) {
            s.material = MATERIALS.register(s.key, () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.BOOTS, s.defense[0], ArmorItem.Type.LEGGINGS, s.defense[1], ArmorItem.Type.CHESTPLATE, s.defense[2],
                            ArmorItem.Type.HELMET, s.defense[3], ArmorItem.Type.BODY, s.defense[2]),
                    s.enchant, s == Set.VANGUARD ? SoundEvents.ARMOR_EQUIP_IRON : SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.of(s.repair), List.of(new ArmorMaterial.Layer(SoloStatus.id(s.key))), s.toughness, s.knockback));
            Map<ArmorItem.Type, DeferredItem<ArmorItem>> row = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type t : TYPES) {
                row.put(t, ModItems.ITEMS.register(s.key + "_" + t.getName(), () -> new Piece(s, t)));
            }
            ITEMS.put(s, row);
        }
    }

    private SystemArmor() {}

    public static void init() {}

    public static ArmorItem get(Set s, ArmorItem.Type t) {
        return ITEMS.get(s).get(t).get();
    }

    public static List<Item> all() {
        List<Item> out = new ArrayList<>();
        for (Set s : Set.values()) for (ArmorItem.Type t : TYPES) out.add(get(s, t));
        return out;
    }

    /** An armour piece: the material's defence plus what its set adds. */
    public static final class Piece extends ArmorItem {
        private final Set set;
        private ItemAttributeModifiers modifiers;

        Piece(Set set, ArmorItem.Type type) {
            super(set.material, type, new Item.Properties().durability(type.getDurability(set.durability)).rarity(set.rarity));
            this.set = set;
        }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers() {
            if (modifiers != null) return modifiers;
            ItemAttributeModifiers base = super.getDefaultAttributeModifiers();
            ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
            base.modifiers().forEach(e -> b.add(e.attribute(), e.modifier(), e.slot()));
            EquipmentSlotGroup slot = EquipmentSlotGroup.bySlot(getEquipmentSlot());
            switch (set) {
                case STALKER -> {
                    b.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(id("speed"), 0.03, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), slot);
                    b.add(Attributes.ATTACK_SPEED, new AttributeModifier(id("attack_speed"), 0.03, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), slot);
                }
                case ARCANIST -> {
                    irons(b, "max_mana", 25, AttributeModifier.Operation.ADD_VALUE, slot);
                    irons(b, "spell_power", 0.04, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, slot);
                }
                case VANGUARD -> {}
            }
            return modifiers = b.build();
        }

        private void irons(ItemAttributeModifiers.Builder b, String attribute, double value, AttributeModifier.Operation op, EquipmentSlotGroup slot) {
            Holder<net.minecraft.world.entity.ai.attributes.Attribute> h = BuiltInRegistries.ATTRIBUTE
                    .getHolder(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", attribute)).orElse(null);
            if (h != null) b.add(h, new AttributeModifier(id(attribute), value, op), slot);
        }

        private ResourceLocation id(String what) {
            return SoloStatus.id(set.key + "_" + getType().getName() + "_" + what);
        }
    }
}
