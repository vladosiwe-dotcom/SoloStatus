package com.vladosiwe.solostatus.registry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;

/**
 * The Archer's own bows and crossbows, two kinds of each in the Hunter and Shadow grades (SystemWeapons.Grade):
 *  - Longbow: a long draw, a fast and heavy arrow;
 *  - Recurve bow: a quick draw, a lighter arrow — for shooting on the move;
 *  - Heavy crossbow: winds slowly, hits very hard;
 *  - Repeating crossbow: winds fast (it loads itself the moment it is wound, the key may stay held).
 * Draw and wind times are the weapon's own, not vanilla's fixed ones, so the shot and the loading are done here.
 */
public final class SystemRanged {

    public enum Kind {
        LONGBOW("longbow", true, 30, 1.25f, 1.35f),
        RECURVE("recurve_bow", true, 13, 0.95f, 0.9f),
        HEAVY_CROSSBOW("heavy_crossbow", false, 40, 1.15f, 1.7f),
        REPEATER("repeating_crossbow", false, 11, 1.0f, 0.85f);

        public final String key;
        public final boolean bow;
        /** Ticks of drawing for a full shot (bows) or of winding (crossbows), at Hunter grade. */
        public final int ticks;
        public final float velocity;
        public final float damage;

        Kind(String key, boolean bow, int ticks, float velocity, float damage) {
            this.key = key;
            this.bow = bow;
            this.ticks = ticks;
            this.velocity = velocity;
            this.damage = damage;
        }

        /** Shadow grade draws a quarter faster and hits a quarter harder. */
        public int ticks(SystemWeapons.Grade g) {
            return g == SystemWeapons.Grade.SHADOW ? Math.max(6, Math.round(ticks * 0.75f)) : ticks;
        }

        public float damage(SystemWeapons.Grade g) {
            return g == SystemWeapons.Grade.SHADOW ? damage * 1.25f : damage;
        }
    }

    private static final List<DeferredItem<? extends Item>> ITEMS = new ArrayList<>();

    static {
        for (SystemWeapons.Grade g : SystemWeapons.Grade.values()) {
            for (Kind k : Kind.values()) {
                Item.Properties p = new Item.Properties().durability(g == SystemWeapons.Grade.SHADOW ? 1600 : 640).rarity(g.rarity);
                if (g == SystemWeapons.Grade.SHADOW) p = p.fireResistant();
                Item.Properties props = p;
                ITEMS.add(ModItems.ITEMS.register(g.key + "_" + k.key, () -> k.bow ? new Bow(k, g, props) : new Crossbow(k, g, props)));
            }
        }
    }

    private SystemRanged() {}

    public static void init() {}

    public static List<Item> all() {
        List<Item> out = new ArrayList<>();
        for (var i : ITEMS) out.add(i.get());
        return out;
    }

    public static Item get(SystemWeapons.Grade g, Kind k) {
        return ITEMS.get(g.ordinal() * Kind.values().length + k.ordinal()).get();
    }

    /** Draw progress 0..1 of this item in the hands of this entity (for the model's pull states). */
    public static float pull(ItemStack stack, LivingEntity e) {
        if (e == null || e.getUseItem() != stack) return 0;
        int ticks = stack.getItem() instanceof Bow b ? b.draw : stack.getItem() instanceof Crossbow c ? c.wind : 20;
        return Math.min(1f, (stack.getUseDuration(e) - e.getUseItemRemainingTicks()) / (float) ticks);
    }

    // ---------------------------------------------------------------- bows

    public static final class Bow extends BowItem {
        final Kind kind;
        final int draw;
        final float damage;

        Bow(Kind kind, SystemWeapons.Grade grade, Properties p) {
            super(p);
            this.kind = kind;
            this.draw = kind.ticks(grade);
            this.damage = kind.damage(grade);
        }

        /** Vanilla's shot with this bow's own draw time and arrow speed. */
        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity shooter, int remaining) {
            if (!(shooter instanceof Player player)) return;
            ItemStack ammo = player.getProjectile(stack);
            if (ammo.isEmpty()) return;
            int held = this.getUseDuration(stack, shooter) - remaining;
            held = net.neoforged.neoforge.event.EventHooks.onArrowLoose(stack, level, player, held, true);
            if (held < 0) return;
            float f = getPowerForTime(Math.round(held * 20f / draw)); // vanilla's curve, stretched to this bow
            if (f < 0.1f) return;
            List<ItemStack> arrows = draw(stack, ammo, player);
            if (level instanceof ServerLevel server && !arrows.isEmpty()) {
                this.shoot(server, player, player.getUsedItemHand(), stack, arrows, f * 3.0f * kind.velocity, 1.0f, f == 1.0f, null);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f,
                    1.0f / (level.getRandom().nextFloat() * 0.4f + 1.2f) + f * 0.5f - (kind == Kind.LONGBOW ? 0.15f : 0));
            player.awardStat(Stats.ITEM_USED.get(this));
        }

        @Override
        public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectile, ItemStack weapon) {
            arrow.setBaseDamage(arrow.getBaseDamage() * damage);
            return arrow;
        }

        @Override
        public int getDefaultProjectileRange() {
            return kind == Kind.LONGBOW ? 20 : 15;
        }
    }

    // ---------------------------------------------------------------- crossbows

    public static final class Crossbow extends CrossbowItem {
        final Kind kind;
        final int wind;
        final float damage;

        Crossbow(Kind kind, SystemWeapons.Grade grade, Properties p) {
            super(p);
            this.kind = kind;
            this.wind = kind.ticks(grade);
            this.damage = kind.damage(grade);
        }

        /** Winding sounds from vanilla; once wound for this crossbow's own time it is loaded. */
        @Override
        public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
            super.onUseTick(level, user, stack, remaining);
            int held = stack.getUseDuration(user) - remaining;
            if (!level.isClientSide && held >= wind && !isCharged(stack) && load(user, stack)) {
                level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1f,
                        1f / (level.getRandom().nextFloat() * 0.5f + 1f) + 0.2f);
                if (kind == Kind.REPEATER) user.stopUsingItem(); // loaded: ready to shoot at once
            }
        }

        /** Letting go early does not load it (vanilla would after its own fixed time). */
        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
            int held = this.getUseDuration(stack, user) - remaining;
            if (!level.isClientSide && held >= wind && !isCharged(stack) && load(user, stack)) {
                level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1f, 1f);
            }
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity user) {
            return Math.max(wind + 3, 72000); // held as long as the key is
        }

        private static boolean load(LivingEntity user, ItemStack crossbow) {
            if (!(user instanceof Player p)) return false;
            ItemStack ammo = p.getProjectile(crossbow);
            boolean creative = p.getAbilities().instabuild;
            if (ammo.isEmpty() && !creative) return false;
            ItemStack loaded = ammo.isEmpty() ? new ItemStack(Items.ARROW) : ammo.copyWithCount(1);
            if (!creative && !ammo.isEmpty()) ammo.shrink(1);
            crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(loaded));
            return true;
        }

        @Override
        public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectile, ItemStack weapon) {
            arrow.setBaseDamage(arrow.getBaseDamage() * damage);
            return arrow;
        }
    }
}
