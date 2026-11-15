package com.vladosiwe.solostatus.achievement;

import com.vladosiwe.solostatus.data.PlayerData;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Predicate;

/**
 * Slayer titles: kill enough of one kind of mob and the System names you after it. While the title is worn you deal
 * more damage to that kind and take less from it. Each kind counts its own fair kills (counter "slay_<id>"), so the
 * title is earned by hunting that mob, not by farming any other.
 */
public final class SlayerTitles {
    /**
     * @param kills   fair kills of this kind needed for the title
     * @param damage  extra damage dealt to this kind while the title is worn (0.12 = +12%)
     * @param defense less damage taken from this kind while the title is worn (0.10 = -10%)
     */
    public record Slayer(String id, Item icon, int kills, double damage, double defense, Predicate<LivingEntity> matches) {
        public String counter() {
            return "slay_" + id;
        }
    }

    public static final List<Slayer> ALL = List.of(
            new Slayer("zombie_slayer", Items.ROTTEN_FLESH, 400, 0.12, 0.10,
                    e -> is(e, EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER)),
            new Slayer("spider_slayer", Items.SPIDER_EYE, 250, 0.12, 0.10, e -> e.getType().is(EntityTypeTags.ARTHROPOD)),
            new Slayer("creeper_slayer", Items.GUNPOWDER, 150, 0.15, 0.30, e -> is(e, EntityType.CREEPER)),
            new Slayer("ender_slayer", Items.ENDER_PEARL, 200, 0.12, 0.10, e -> is(e, EntityType.ENDERMAN, EntityType.ENDERMITE, EntityType.SHULKER)),
            new Slayer("piglin_slayer", Items.GOLD_NUGGET, 300, 0.12, 0.10,
                    e -> is(e, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.ZOMBIFIED_PIGLIN, EntityType.HOGLIN, EntityType.ZOGLIN)),
            new Slayer("illager_slayer", Items.CROSSBOW, 150, 0.15, 0.10, e -> e.getType().is(EntityTypeTags.ILLAGER) || is(e, EntityType.RAVAGER, EntityType.VEX)),
            new Slayer("nether_slayer", Items.BLAZE_ROD, 250, 0.12, 0.12,
                    e -> is(e, EntityType.BLAZE, EntityType.GHAST, EntityType.MAGMA_CUBE, EntityType.WITHER_SKELETON, EntityType.STRIDER)),
            new Slayer("deep_sea_slayer", Items.PRISMARINE_SHARD, 120, 0.15, 0.12,
                    e -> is(e, EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN, EntityType.DROWNED)),
            new Slayer("slime_slayer", Items.SLIME_BALL, 300, 0.12, 0.10, e -> is(e, EntityType.SLIME, EntityType.MAGMA_CUBE)),
            new Slayer("phantom_slayer", Items.PHANTOM_MEMBRANE, 100, 0.20, 0.15, e -> is(e, EntityType.PHANTOM)),
            new Slayer("witch_slayer", Items.CAULDRON, 60, 0.15, 0.20, e -> is(e, EntityType.WITCH)),
            new Slayer("breeze_slayer", Items.BREEZE_ROD, 80, 0.15, 0.15, e -> is(e, EntityType.BREEZE, EntityType.BOGGED))
    );

    private SlayerTitles() {}

    private static boolean is(LivingEntity e, EntityType<?>... types) {
        for (EntityType<?> t : types) if (e.getType() == t) return true;
        return false;
    }

    /** Counts a fair kill for every slayer kind the mob belongs to. */
    public static void onKill(PlayerData d, LivingEntity dead) {
        for (Slayer s : ALL) if (s.matches().test(dead)) d.addCounter(s.counter(), 1);
    }

    /** Extra damage to the target from the worn slayer title (0 if none applies). */
    public static double damageBonus(PlayerData d, LivingEntity target) {
        for (Slayer s : ALL) if (d.titleIs(s.id()) && s.matches().test(target)) return s.damage();
        return 0;
    }

    /** Share of damage from this attacker taken away by the worn slayer title (0 if none applies). */
    public static double defense(PlayerData d, LivingEntity attacker) {
        for (Slayer s : ALL) if (d.titleIs(s.id()) && s.matches().test(attacker)) return s.defense();
        return 0;
    }
}
