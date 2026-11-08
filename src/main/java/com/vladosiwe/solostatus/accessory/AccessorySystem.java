package com.vladosiwe.solostatus.accessory;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.network.ModNetwork;
import com.vladosiwe.solostatus.network.Notices;
import com.vladosiwe.solostatus.network.Payloads;
import com.vladosiwe.solostatus.quality.QualityRarity;
import com.vladosiwe.solostatus.registry.ModAttachments;
import com.vladosiwe.solostatus.registry.ModComponents;
import com.vladosiwe.solostatus.registry.ModItems;
import com.vladosiwe.solostatus.stats.AttributeSystem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Jewellery: rolling, wearing and the bonuses of what is worn.
 *
 * Slots: the System has its own 3 (two rings, one amulet), filled by using the item; Curios ring/necklace slots
 * also count when Curios is installed. Whatever the source, at most 2 rings and 1 amulet give bonuses, and each
 * bonus has a total cap ({@link Affix#cap}). Bonuses are recomputed once a second only when what is worn changes.
 */
public final class AccessorySystem {
    public static final int SLOTS = 3; // 0, 1 — rings; 2 — amulet

    /** Where a piece dropped: decides the rarity odds. */
    public enum Source { CHEST, RARE_CHEST, BOSS }

    private static final ResourceLocation HEALTH = SoloStatus.id("acc_health");
    private static final ResourceLocation ARMOR = SoloStatus.id("acc_armor");
    private static final ResourceLocation SPEED = SoloStatus.id("acc_speed");
    private static final ResourceLocation ATK_SPEED = SoloStatus.id("acc_attack_speed");
    private static final ResourceLocation LUCK = SoloStatus.id("acc_luck");

    private AccessorySystem() {}

    // ============================ rolling ============================

    /** Rarity odds (Common, Uncommon, Rare, Epic, Legendary); LUCK shifts a little weight to the top. */
    public static QualityRarity rollRarity(Source source, RandomSource random, double luck) {
        double[] w = switch (source) {
            case CHEST -> new double[]{50, 32, 14, 3.5, 0.5};
            case RARE_CHEST -> new double[]{20, 35, 32, 11, 2};
            case BOSS -> new double[]{0, 0, 45, 42, 13};
        };
        w[3] *= 1 + luck * 2;
        w[4] *= 1 + luck * 3;
        double total = 0;
        for (double v : w) total += v;
        double r = random.nextDouble() * total;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0) return QualityRarity.VALUES[i];
        }
        return QualityRarity.COMMON;
    }

    private static int affixCount(QualityRarity r) {
        return switch (r) {
            case COMMON -> 1;
            case UNCOMMON, RARE -> 2;
            case EPIC -> 3;
            case LEGENDARY -> 4;
        };
    }

    /** Where in an affix's range a rarity lands (0 = min, 1 = max). */
    private static double[] band(QualityRarity r) {
        return switch (r) {
            case COMMON -> new double[]{0.0, 0.3};
            case UNCOMMON -> new double[]{0.15, 0.45};
            case RARE -> new double[]{0.3, 0.65};
            case EPIC -> new double[]{0.5, 0.85};
            case LEGENDARY -> new double[]{0.75, 1.0};
        };
    }

    public static AccessoryData roll(AccessoryKind kind, QualityRarity rarity, RandomSource random) {
        List<Affix> pool = new ArrayList<>();
        for (Affix a : Affix.VALUES) if (a.allowedOn(kind)) pool.add(a);
        double[] band = band(rarity);
        List<AccessoryData.Roll> rolls = new ArrayList<>();
        for (int i = 0; i < affixCount(rarity) && !pool.isEmpty(); i++) {
            Affix a = pool.remove(random.nextInt(pool.size()));
            double f = band[0] + random.nextDouble() * (band[1] - band[0]);
            double value = a.min + (a.max - a.min) * f;
            rolls.add(new AccessoryData.Roll(a.key, (float) (a.percent ? Math.round(value * 1000) / 1000.0 : Math.round(value * 10) / 10.0)));
        }
        return new AccessoryData(rarity.ordinal(), rolls);
    }

    /** A new rolled ring or amulet (kind chosen at random when null). */
    public static ItemStack create(AccessoryKind kind, Source source, RandomSource random, double luck) {
        if (kind == null) kind = random.nextFloat() < 0.6f ? AccessoryKind.RING : AccessoryKind.AMULET;
        ItemStack stack = new ItemStack(kind == AccessoryKind.RING ? ModItems.RING.get() : ModItems.AMULET.get());
        apply(stack, roll(kind, rollRarity(source, random, luck), random));
        return stack;
    }

    public static void apply(ItemStack stack, AccessoryData data) {
        stack.set(ModComponents.ACCESSORY.get(), data);
        stack.set(DataComponents.RARITY, data.tier().vanilla);
    }

    // ============================ wearing ============================

    public static ItemStackHandler slots(Player player) {
        return player.getData(ModAttachments.ACCESSORIES);
    }

    /** Use of a ring/amulet: into the System slot (a ring takes a free ring slot, otherwise swaps with the first). */
    public static void equipFromHand(ServerPlayer p, InteractionHand hand) {
        ItemStack held = p.getItemInHand(hand);
        if (!(held.getItem() instanceof AccessoryItem item)) return;
        if (held.get(ModComponents.ACCESSORY.get()) == null) apply(held, roll(item.kind, rollRarity(Source.CHEST, p.getRandom(), 0), p.getRandom()));
        ItemStackHandler slots = slots(p);
        int target = item.kind == AccessoryKind.AMULET ? 2 : slots.getStackInSlot(0).isEmpty() ? 0 : slots.getStackInSlot(1).isEmpty() ? 1 : 0;
        ItemStack old = slots.getStackInSlot(target);
        slots.setStackInSlot(target, held.copy());
        p.setItemInHand(hand, old);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(), p.getSoundSource(), 0.8f, 1.3f);
        refresh(p);
    }

    /** From the System window: back to the inventory (or dropped if it is full). */
    public static void unequip(ServerPlayer p, int slot) {
        if (slot < 0 || slot >= SLOTS) return;
        ItemStackHandler slots = slots(p);
        ItemStack s = slots.getStackInSlot(slot);
        if (s.isEmpty()) return;
        slots.setStackInSlot(slot, ItemStack.EMPTY);
        if (!p.getInventory().add(s)) p.drop(s, false);
        refresh(p);
    }

    /** What counts: Curios first, then the System slots, up to the per-kind limit. */
    public static List<ItemStack> worn(Player p) {
        List<ItemStack> all = new ArrayList<>(CuriosAccess.equipped(p));
        ItemStackHandler slots = slots(p);
        for (int i = 0; i < SLOTS; i++) all.add(slots.getStackInSlot(i));
        Map<AccessoryKind, Integer> used = new EnumMap<>(AccessoryKind.class);
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : all) {
            if (s.isEmpty() || !(s.getItem() instanceof AccessoryItem item) || s.get(ModComponents.ACCESSORY.get()) == null) continue;
            int n = used.getOrDefault(item.kind, 0);
            if (n >= item.kind.limit) continue;
            used.put(item.kind, n + 1);
            out.add(s);
        }
        return out;
    }

    public static Map<Affix, Double> bonuses(List<ItemStack> worn) {
        Map<Affix, Double> sum = new EnumMap<>(Affix.class);
        for (ItemStack s : worn) {
            AccessoryData data = s.get(ModComponents.ACCESSORY.get());
            if (data == null) continue;
            for (AccessoryData.Roll r : data.rolls()) {
                Affix a = Affix.byKey(r.affix());
                if (a != null) sum.merge(a, (double) r.value(), Double::sum);
            }
        }
        sum.replaceAll((a, v) -> Math.min(a.cap, v));
        return sum;
    }

    /** Once a second: if what is worn changed, recompute bonuses, re-apply attributes and tell the client. */
    public static void tick(ServerPlayer p, PlayerData d) {
        List<ItemStack> worn = worn(p);
        int fp = 1;
        for (ItemStack s : worn) fp = 31 * fp + ItemStack.hashItemAndComponents(s);
        ItemStackHandler slots = slots(p);
        for (int i = 0; i < SLOTS; i++) fp = 31 * fp + ItemStack.hashItemAndComponents(slots.getStackInSlot(i));
        if (fp == d.gearFingerprint) return;
        d.gearFingerprint = fp;
        d.gear.clear();
        d.gear.putAll(bonuses(worn));
        AttributeSystem.apply(p);
        sync(p, d);
    }

    /** Forces a recompute on the next tick (login, respawn, a change of slots). */
    public static void refresh(ServerPlayer p) {
        Hunter.get(p).gearFingerprint = 0;
        tick(p, Hunter.get(p));
    }

    private static void sync(ServerPlayer p, PlayerData d) {
        List<ItemStack> system = new ArrayList<>();
        ItemStackHandler slots = slots(p);
        for (int i = 0; i < SLOTS; i++) system.add(slots.getStackInSlot(i).copy());
        CompoundTag gear = new CompoundTag();
        d.gear.forEach((a, v) -> gear.putDouble(a.key, v));
        ModNetwork.toPlayer(p, new Payloads.AccessorySync(system, gear));
    }

    /** Attribute part of the bonuses (called from AttributeSystem.apply). */
    public static void applyAttributes(ServerPlayer p, PlayerData d) {
        AttributeSystem.set(p, Attributes.MAX_HEALTH, HEALTH, d.gear(Affix.MAX_HEALTH), AttributeModifier.Operation.ADD_VALUE);
        AttributeSystem.set(p, Attributes.ARMOR, ARMOR, d.gear(Affix.ARMOR), AttributeModifier.Operation.ADD_VALUE);
        AttributeSystem.set(p, Attributes.MOVEMENT_SPEED, SPEED, d.gear(Affix.MOVE_SPEED), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        AttributeSystem.set(p, Attributes.ATTACK_SPEED, ATK_SPEED, d.gear(Affix.ATTACK_SPEED), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        AttributeSystem.set(p, Attributes.LUCK, LUCK, d.gear(Affix.LUCK), AttributeModifier.Operation.ADD_VALUE);
    }

    /** A boss gave jewellery: a short System notice, like the other boss rewards. */
    public static void announce(ServerPlayer p, ItemStack stack) {
        Notices.send(p, Notices.INFO, net.minecraft.network.chat.Component.translatable("solostatus.notice.accessory"), stack.getHoverName(), stack.copy());
    }
}
