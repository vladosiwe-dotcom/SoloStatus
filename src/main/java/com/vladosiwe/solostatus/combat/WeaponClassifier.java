package com.vladosiwe.solostatus.combat;

import com.vladosiwe.solostatus.SoloStatus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;

import java.util.EnumMap;
import java.util.Map;

/**
 * Определение типа оружия для любого предмета, включая предметы других модов.
 * Порядок: 1) теги solostatus:weapons/&lt;тип&gt; (настраиваются датапаком), 2) классы предметов Minecraft,
 * 3) ключевые слова в id предмета (dagger, spear, greatsword, staff...), что покрывает оружие Epic Fight
 * и Iron's Spells без жёсткой зависимости.
 */
public final class WeaponClassifier {
    private static final Map<WeaponType, TagKey<Item>> TAGS = new EnumMap<>(WeaponType.class);

    static {
        for (WeaponType t : WeaponType.VALUES) {
            if (t == WeaponType.NONE || t == WeaponType.FIST) continue;
            TAGS.put(t, TagKey.create(Registries.ITEM, SoloStatus.id("weapons/" + t.key)));
        }
    }

    private WeaponClassifier() {}

    public static WeaponType classify(ItemStack stack) {
        if (stack.isEmpty()) return WeaponType.FIST;
        for (var e : TAGS.entrySet()) if (stack.is(e.getValue())) return e.getKey();

        Item item = stack.getItem();
        if (item instanceof ShieldItem) return WeaponType.SHIELD;
        if (item instanceof CrossbowItem) return WeaponType.CROSSBOW;
        if (item instanceof BowItem) return WeaponType.BOW;
        if (item instanceof TridentItem) return WeaponType.SPEAR;
        if (item instanceof MaceItem) return WeaponType.MACE;

        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (has(path, "dagger", "knife", "kunai", "sai", "dirk", "stiletto")) return WeaponType.DAGGER;
        if (has(path, "greatsword", "great_sword", "claymore", "zweihander", "greataxe", "great_axe", "colossal")) return WeaponType.GREATSWORD;
        if (has(path, "spear", "lance", "halberd", "glaive", "pike", "javelin")) return WeaponType.SPEAR;
        if (has(path, "staff", "wand", "scepter", "sceptre")) return WeaponType.STAFF;
        if (has(path, "hammer", "maul", "warhammer", "mace", "club")) return WeaponType.MACE;
        if (item instanceof AxeItem || has(path, "battleaxe", "battle_axe")) return WeaponType.AXE;
        if (item instanceof SwordItem || has(path, "sword", "katana", "tachi", "uchigatana", "rapier", "saber", "sabre", "scimitar", "blade")) return WeaponType.SWORD;
        if (item instanceof ProjectileWeaponItem) return WeaponType.BOW;
        return WeaponType.NONE;
    }

    /** Предмет, который может иметь качество (любое оружие, кроме пустой руки). */
    public static boolean isQualityWeapon(ItemStack stack) {
        WeaponType t = classify(stack);
        return t != WeaponType.NONE && t != WeaponType.FIST && stack.getMaxStackSize() == 1;
    }

    private static boolean has(String path, String... words) {
        for (String w : words) if (path.contains(w)) return true;
        return false;
    }
}
