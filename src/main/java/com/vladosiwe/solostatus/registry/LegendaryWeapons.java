package com.vladosiwe.solostatus.registry;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.clazz.HunterClass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One legendary weapon per class, modelled in Blockbench (tools/blockbench/make_legendary.py). Each never breaks,
 * does not burn, has a property of its own (read by the combat code) and awakens its class's legendary skill the
 * first time a hunter of that class holds it (Skills: source LEGENDARY, cast only while holding it). They drop
 * from bosses for the class of the hunter who won (CombatEvents.onDrops).
 */
public final class LegendaryWeapons {
    public enum Kind {
        MOUNTAIN_CLEAVER("mountain_cleaver", HunterClass.WARRIOR, "mountain_fall"),
        DUSK_FANG("dusk_fang", HunterClass.THIEF, "thousand_shadows"),
        STARWELL_STAFF("starwell_staff", HunterClass.MAGE, "star_well"),
        LAST_BASTION("last_bastion", HunterClass.DEFENDER, "unbreakable_line"),
        SKYHUNT_BOW("skyhunt_bow", HunterClass.ARCHER, "heavens_verdict"),
        DAWN_SCEPTER("dawn_scepter", HunterClass.SUPPORT, "daybreak");

        public final String key;
        public final HunterClass owner;
        /** The legendary skill this weapon awakens. */
        public final String skill;

        Kind(String key, HunterClass owner, String skill) {
            this.key = key;
            this.owner = owner;
            this.skill = skill;
        }

        public Item item() {
            return ITEMS.get(this).get();
        }

        /** The counter that marks the weapon as held once by this hunter (the legendary skill's requirement). */
        public String counter() {
            return "legendary_" + key;
        }
    }

    private static final Map<Kind, DeferredItem<? extends Item>> ITEMS = new EnumMap<>(Kind.class);

    static {
        ITEMS.put(Kind.MOUNTAIN_CLEAVER, ModItems.ITEMS.register("mountain_cleaver", () -> new Blade(Kind.MOUNTAIN_CLEAVER, melee(Kind.MOUNTAIN_CLEAVER, 13, 0.9).build())));
        ITEMS.put(Kind.DUSK_FANG, ModItems.ITEMS.register("dusk_fang", () -> new Blade(Kind.DUSK_FANG, melee(Kind.DUSK_FANG, 8, 2.2).build())));
        ITEMS.put(Kind.STARWELL_STAFF, ModItems.ITEMS.register("starwell_staff", () -> {
            var b = melee(Kind.STARWELL_STAFF, 6, 1.2);
            irons(b, Kind.STARWELL_STAFF, "spell_power", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            irons(b, Kind.STARWELL_STAFF, "max_mana", 100, AttributeModifier.Operation.ADD_VALUE);
            irons(b, Kind.STARWELL_STAFF, "mana_regen", 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            return new Blade(Kind.STARWELL_STAFF, b.build());
        }));
        ITEMS.put(Kind.LAST_BASTION, ModItems.ITEMS.register("last_bastion", () -> new Shield(Kind.LAST_BASTION)));
        ITEMS.put(Kind.SKYHUNT_BOW, ModItems.ITEMS.register("skyhunt_bow", () -> new Bow(Kind.SKYHUNT_BOW)));
        ITEMS.put(Kind.DAWN_SCEPTER, ModItems.ITEMS.register("dawn_scepter", () -> {
            var b = melee(Kind.DAWN_SCEPTER, 9, 1.1);
            irons(b, Kind.DAWN_SCEPTER, "holy_spell_power", 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            irons(b, Kind.DAWN_SCEPTER, "max_mana", 50, AttributeModifier.Operation.ADD_VALUE);
            return new Blade(Kind.DAWN_SCEPTER, b.build());
        }));
    }

    private LegendaryWeapons() {}

    /** Called from ModItems so these are registered with the other items. */
    public static void init() {}

    public static List<Item> all() {
        return java.util.Arrays.stream(Kind.values()).map(Kind::item).toList();
    }

    /** The legendary weapon made for this class. */
    public static Kind forClass(HunterClass c) {
        for (Kind k : Kind.values()) if (k.owner == c) return k;
        return null;
    }

    /** The legendary weapon an item stack is, or null. */
    public static Kind of(ItemStack stack) {
        return stack.getItem() instanceof Legendary l ? l.kind() : null;
    }

    /** True when the player holds this legendary weapon in either hand. */
    public static boolean holds(Player p, Kind k) {
        return of(p.getMainHandItem()) == k || of(p.getOffhandItem()) == k;
    }

    private static Item.Properties props() {
        return new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant().component(DataComponents.UNBREAKABLE, new Unbreakable(true));
    }

    private static ItemAttributeModifiers.Builder melee(Kind k, double damage, double speed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage - 1, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed - 4, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
    }

    private static void irons(ItemAttributeModifiers.Builder b, Kind k, String attribute, double value, AttributeModifier.Operation op) {
        BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", attribute))
                .ifPresent(h -> b.add(h, new AttributeModifier(SoloStatus.id(k.key + "_" + attribute), value, op), EquipmentSlotGroup.MAINHAND));
    }

    /** What every legendary weapon shows: its class, its property and the skill it awakens. */
    private static void describe(Kind k, List<Component> tip) {
        tip.add(Component.translatable("solostatus.legendary.tooltip", k.owner.displayName()).withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("solostatus.legendary." + k.key + ".passive").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("solostatus.legendary.skill", Component.translatable("solostatus.skill." + k.skill))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public interface Legendary {
        Kind kind();
    }

    // ---------------------------------------------------------------- melee and caster weapons

    public static final class Blade extends SwordItem implements Legendary {
        private final Kind kind;

        Blade(Kind kind, ItemAttributeModifiers attributes) {
            super(Tiers.NETHERITE, props().attributes(attributes));
            this.kind = kind;
        }

        @Override
        public Kind kind() {
            return kind;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
            describe(kind, tip);
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return false; // the glowing parts are in the model
        }
    }

    // ---------------------------------------------------------------- the Defender's shield

    public static final class Shield extends ShieldItem implements Legendary {
        private final Kind kind;

        Shield(Kind kind) {
            super(props().attributes(ItemAttributeModifiers.builder()
                    .add(Attributes.ARMOR, new AttributeModifier(SoloStatus.id(kind.key + "_armor"), 4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HAND)
                    .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(SoloStatus.id(kind.key + "_toughness"), 2, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HAND)
                    .build()));
            this.kind = kind;
        }

        @Override
        public Kind kind() {
            return kind;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
            describe(kind, tip);
        }
    }

    // ---------------------------------------------------------------- the Archer's bow

    /** Draws in 0.9 s, shoots faster and harder arrows and needs none: it shapes its own from light. */
    public static final class Bow extends BowItem implements Legendary {
        public static final int DRAW_TICKS = 18;
        private final Kind kind;

        Bow(Kind kind) {
            super(props());
            this.kind = kind;
        }

        @Override
        public Kind kind() {
            return kind;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
            describe(kind, tip);
        }

        /** Can always be drawn: no arrows needed. */
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }

        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity shooter, int remaining) {
            if (!(shooter instanceof Player player)) return;
            int held = this.getUseDuration(stack, shooter) - remaining;
            float f = getPowerForTime(Math.round(held * 20f / DRAW_TICKS));
            if (f < 0.1f) return;
            if (level instanceof ServerLevel server) {
                Arrow arrow = new Arrow(server, player, new ItemStack(Items.ARROW), stack);
                arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, f * 3.0f * 1.25f, 0.5f);
                arrow.setBaseDamage(arrow.getBaseDamage() * 1.4);
                arrow.setCritArrow(f >= 1f);
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY; // shaped from light: nothing to pick up
                server.addFreshEntity(arrow);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1f,
                    1f / (level.getRandom().nextFloat() * 0.4f + 1.2f) + f * 0.5f);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.6f);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
    }

    /** Draw progress 0..1 of the bow in this entity's hands (for the model's pull states). */
    public static float pull(ItemStack stack, LivingEntity e) {
        if (e == null || e.getUseItem() != stack) return 0;
        return Math.min(1f, (stack.getUseDuration(e) - e.getUseItemRemainingTicks()) / (float) Bow.DRAW_TICKS);
    }
}
