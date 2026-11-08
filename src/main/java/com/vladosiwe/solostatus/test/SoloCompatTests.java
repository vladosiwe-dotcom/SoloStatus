package com.vladosiwe.solostatus.test;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.clazz.ClassSystem;
import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.combat.WeaponType;
import com.vladosiwe.solostatus.compat.CompatManager;
import com.vladosiwe.solostatus.compat.epicfight.EpicFightCompat;
import com.vladosiwe.solostatus.compat.ironsspells.IronsSpellsCompat;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.level.LevelSystem;
import com.vladosiwe.solostatus.mastery.MasterySystem;
import com.vladosiwe.solostatus.skill.SkillAnimations;
import com.vladosiwe.solostatus.skill.SkillUnlockSystem;
import com.vladosiwe.solostatus.stats.AttributeSystem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Integration and progression tests on a real server. Epic Fight / Iron's Spells checks run when those mods
 * are present; with SOLO_COMPAT_REQUIRED=1 (set by CI) their absence is a failure, not a skip.
 */
@GameTestHolder(SoloStatus.MODID)
@PrefixGameTestTemplate(false)
public final class SoloCompatTests {
    private SoloCompatTests() {}

    private static boolean required() { return "1".equals(System.getenv("SOLO_COMPAT_REQUIRED")); }

    private static ServerPlayer hunter(GameTestHelper h, HunterClass c) {
        ServerPlayer p = TestPlayers.create(h);
        ClassSystem.chooseClass(p, c);
        return p;
    }

    /** Epic Fight: animations and dodge/parry hooks connected; every animation a skill uses exists in this version. */
    @GameTest(template = "empty")
    public static void epicFightIntegration(GameTestHelper h) {
        if (!ModList.get().isLoaded("epicfight")) {
            if (required()) h.fail("Epic Fight is not loaded");
            h.succeed();
            return;
        }
        h.assertTrue(CompatManager.epicFightLoaded(), "CompatManager does not see Epic Fight");
        h.assertTrue(EpicFightCompat.animationsReady(), "Epic Fight animations are not connected: " + EpicFightCompat.animationsError());
        h.assertTrue(EpicFightCompat.eventsHooked(), "Epic Fight dodge/parry events are not connected: " + EpicFightCompat.eventsError());
        h.assertTrue(EpicFightCompat.patchAccessReady(), "Epic Fight dodge/parry handlers cannot reach the player");
        List<String> missing = EpicFightCompat.missingAnimations(SkillAnimations.allAnimations());
        h.assertTrue(missing.isEmpty(), "animations missing in this Epic Fight version: " + missing);
        h.succeed();
    }

    /** Iron's Spells: spell cast event hooked, INT/class bonuses reach Iron's attributes, magic damage → school mastery. */
    @GameTest(template = "empty")
    public static void ironsSpellsIntegration(GameTestHelper h) {
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            if (required()) h.fail("Iron's Spells is not loaded");
            h.succeed();
            return;
        }
        h.assertTrue(IronsSpellsCompat.castHooked(), "SpellOnCastEvent is not hooked");
        ServerPlayer p = hunter(h, HunterClass.MAGE);
        AttributeSystem.apply(p);
        var power = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power"));
        h.assertTrue(power.isPresent(), "irons_spellbooks:spell_power attribute not found");
        AttributeInstance inst = p.getAttribute(power.get());
        h.assertTrue(inst != null && inst.getModifier(SoloStatus.id("irons_spell_power")) != null, "Mage spell power bonus is not applied");

        var key = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fire_magic"));
        var holder = p.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(key);
        h.assertTrue(holder.isPresent(), "irons_spellbooks:fire_magic damage type not found");
        DamageSource fire = new DamageSource(holder.get(), p);
        h.assertTrue("fire".equals(CompatManager.ironsSchool(fire)), "fire magic is not recognised as the fire school: " + CompatManager.ironsSchool(fire));

        // a kill with fire magic grows fire mastery
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        z.hurt(fire, 1000f);
        PlayerData d = Hunter.get(p);
        h.assertTrue(d.counter("magic_kills") == 1, "magic kill not counted: " + d.counter("magic_kills"));
        h.assertTrue(d.mastery.getOrDefault("magic:fire", 0.0) > 0, "fire mastery did not grow: " + d.mastery);
        h.succeed();
    }

    /** A fair kill gives system XP, sword mastery and counters; a mob without AI (farm dummy) gives nothing. */
    @GameTest(template = "empty")
    public static void killsGiveXpAndMastery(GameTestHelper h) {
        ServerPlayer p = hunter(h, HunterClass.WARRIOR);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        PlayerData d = Hunter.get(p);

        Zombie dummy = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        dummy.setNoAi(true);
        long xpBefore = d.xp; int levelBefore = d.level;
        dummy.hurt(p.damageSources().playerAttack(p), 1000f);
        h.assertTrue(d.xp == xpBefore && d.level == levelBefore, "a mob without AI must give no XP");
        h.assertTrue(d.counter("kills") == 0, "a mob without AI must not count");

        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        z.hurt(p.damageSources().playerAttack(p), 1000f);
        h.assertTrue(d.xp > xpBefore || d.level > levelBefore, "a fair kill gave no XP");
        h.assertTrue(d.counter("kills") == 1 && d.counter("melee_kills") == 1, "kill counters: " + d.counter("kills") + "/" + d.counter("melee_kills"));
        h.assertTrue(d.mastery.getOrDefault(WeaponType.SWORD.key, 0.0) > 0, "sword mastery did not grow: " + d.mastery);
        h.succeed();
    }

    /** Awakening: sword mastery 10 at level 5 awakens Cross Slash by itself, for free. */
    @GameTest(template = "empty")
    public static void masteryAwakensSkill(GameTestHelper h) {
        ServerPlayer p = hunter(h, HunterClass.MAGE); // any class: awakenings are not class-bound
        PlayerData d = Hunter.get(p);
        LevelSystem.setLevel(p, 5);
        int pointsBefore = d.skillPoints;
        for (int i = 0; i < 400 && MasterySystem.level(d, WeaponType.SWORD) < 10; i++) MasterySystem.addWeapon(p, d, WeaponType.SWORD, 50);
        h.assertTrue(MasterySystem.level(d, WeaponType.SWORD) >= 10, "sword mastery did not reach 10");
        d.unlockCheckPending = true;
        SkillUnlockSystem.check(p);
        h.assertTrue(d.hasSkill("cross_slash"), "Cross Slash did not awaken");
        h.assertTrue(d.skillPoints == pointsBefore, "awakening must be free");
        h.succeed();
    }

    /** Thorns (and other indirect damage) gives XP for a fair kill, but no weapon mastery and no melee kill. */
    @GameTest(template = "empty")
    public static void indirectKillGivesNoWeaponMastery(GameTestHelper h) {
        ServerPlayer p = hunter(h, HunterClass.WARRIOR);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        PlayerData d = Hunter.get(p);
        long xpBefore = d.xp; int levelBefore = d.level;
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        z.hurt(p.damageSources().thorns(p), 1000f);
        h.assertTrue(d.xp > xpBefore || d.level > levelBefore, "a fair thorns kill gave no XP");
        h.assertTrue(d.counter("melee_kills") == 0 && d.counter("magic_kills") == 0, "thorns counted as melee/magic kill");
        h.assertTrue(d.mastery.getOrDefault(WeaponType.SWORD.key, 0.0) == 0, "thorns grew sword mastery: " + d.mastery);
        h.assertTrue(d.mastery.keySet().stream().noneMatch(k -> k.startsWith("magic:")), "thorns grew magic mastery: " + d.mastery);
        h.succeed();
    }

    /** A boss-sized mob without AI (statue, command dummy) gives no boss XP. */
    @GameTest(template = "empty")
    public static void bossWithoutAiGivesNothing(GameTestHelper h) {
        ServerPlayer p = hunter(h, HunterClass.WARRIOR);
        PlayerData d = Hunter.get(p);
        Zombie statue = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        statue.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);
        statue.setHealth(300);
        statue.setNoAi(true);
        long xpBefore = d.xp; int levelBefore = d.level;
        statue.hurt(p.damageSources().playerAttack(p), 10000f);
        h.assertTrue(d.xp == xpBefore && d.level == levelBefore, "a boss without AI gave XP");
        h.assertTrue(d.bossesKilled.isEmpty(), "a boss without AI was counted");
        h.succeed();
    }

    /** General techniques: any class buys Parry with a skill point, it lands in a free slot and opens its window. */
    @GameTest(template = "empty")
    public static void generalSkillsAreBoughtByAnyClass(GameTestHelper h) {
        ServerPlayer p = hunter(h, HunterClass.MAGE);
        PlayerData d = Hunter.get(p);
        LevelSystem.setLevel(p, 5);
        int points = d.skillPoints;
        com.vladosiwe.solostatus.skill.SkillSystem.learnOrUpgrade(p, "parry");
        h.assertTrue(d.hasSkill("parry"), "a Mage could not learn Parry");
        h.assertTrue(d.skillPoints == points - 1, "Parry must cost one point");
        int slot = java.util.Arrays.asList(d.slots).indexOf("parry");
        h.assertTrue(slot >= 0, "Parry was not put into a free slot");
        com.vladosiwe.solostatus.skill.SkillSystem.cast(p, slot);
        h.assertTrue(d.stateActive("parry", p.serverLevel().getGameTime()), "Parry window did not open");
        h.assertTrue(com.vladosiwe.solostatus.stats.StatFormulas.usesMana(d), "Mage must use mana");
        h.succeed();
    }

    /**
     * RPG Series (Spell Engine / Spell Power): INT reaches the school attributes, a spell's damage type names its
     * school. Runs when the mods are present; CI runs it in a separate pass with SOLO_RPG_REQUIRED=1.
     */
    @GameTest(template = "empty")
    public static void rpgSeriesIntegration(GameTestHelper h) {
        if (!ModList.get().isLoaded("spell_power")) {
            if ("1".equals(System.getenv("SOLO_RPG_REQUIRED"))) h.fail("Spell Power (RPG Series) is not loaded");
            h.succeed();
            return;
        }
        h.assertTrue(CompatManager.spellPowerLoaded(), "CompatManager does not see Spell Power");
        ServerPlayer p = hunter(h, HunterClass.MAGE);
        AttributeSystem.apply(p);
        var fireAttr = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.fromNamespaceAndPath("spell_power", "fire"));
        h.assertTrue(fireAttr.isPresent(), "spell_power:fire attribute not found");
        AttributeInstance inst = p.getAttribute(fireAttr.get());
        h.assertTrue(inst != null && inst.getModifier(SoloStatus.id("sp_fire")) != null, "INT bonus does not reach spell_power:fire");

        var types = p.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        List<String> spTypes = types.keySet().stream().filter(k -> k.getNamespace().equals("spell_power")).map(ResourceLocation::toString).sorted().toList();
        var fire = types.getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("spell_power", "fire")));
        h.assertTrue(fire.isPresent(), "spell_power:fire damage type not found; spell_power damage types: " + spTypes);
        DamageSource src = new DamageSource(fire.get(), p);
        h.assertTrue("fire".equals(CompatManager.magicSchool(src)), "RPG fire spell is not the fire school: " + CompatManager.magicSchool(src));

        // report how RPG Series weapons are classified (for tuning the id keywords)
        java.util.Map<String, String> seen = new java.util.TreeMap<>();
        for (var item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!List.of("wizards", "rogues", "paladins", "archers").contains(id.getNamespace())) continue;
            ItemStack st = new ItemStack(item);
            if (st.getMaxStackSize() != 1 || item instanceof net.minecraft.world.item.ArmorItem) continue;
            seen.put(id.toString(), com.vladosiwe.solostatus.combat.WeaponClassifier.classify(st).key);
        }
        SoloStatus.LOGGER.info("RPG Series weapons as the System sees them: {}", seen);
        h.succeed();
    }

    private static ItemStack jewel(com.vladosiwe.solostatus.accessory.AccessoryKind kind, String affix, float value) {
        ItemStack st = new ItemStack(kind == com.vladosiwe.solostatus.accessory.AccessoryKind.RING
                ? com.vladosiwe.solostatus.registry.ModItems.RING.get() : com.vladosiwe.solostatus.registry.ModItems.AMULET.get());
        com.vladosiwe.solostatus.accessory.AccessorySystem.apply(st, new com.vladosiwe.solostatus.accessory.AccessoryData(
                com.vladosiwe.solostatus.quality.QualityRarity.RARE.ordinal(), List.of(new com.vladosiwe.solostatus.accessory.AccessoryData.Roll(affix, value))));
        return st;
    }

    /** Jewellery in the System slots: bonuses apply, two rings at most, totals are capped, taking off removes them. */
    @GameTest(template = "empty")
    public static void jewelleryInSystemSlots(GameTestHelper h) {
        var RING = com.vladosiwe.solostatus.accessory.AccessoryKind.RING;
        ServerPlayer p = hunter(h, HunterClass.WARRIOR);
        PlayerData d = Hunter.get(p);
        p.setItemInHand(InteractionHand.MAIN_HAND, jewel(RING, "max_health", 5));
        com.vladosiwe.solostatus.accessory.AccessorySystem.equipFromHand(p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getMainHandItem().isEmpty(), "the ring stayed in hand");
        h.assertTrue(d.gear(com.vladosiwe.solostatus.accessory.Affix.MAX_HEALTH) == 5, "health bonus not counted: " + d.gear);
        AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
        h.assertTrue(hp != null && hp.getModifier(SoloStatus.id("acc_health")) != null, "health modifier not applied");

        p.setItemInHand(InteractionHand.MAIN_HAND, jewel(RING, "crit_chance", 0.05f));
        com.vladosiwe.solostatus.accessory.AccessorySystem.equipFromHand(p, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, jewel(RING, "crit_chance", 0.05f));
        com.vladosiwe.solostatus.accessory.AccessorySystem.equipFromHand(p, InteractionHand.MAIN_HAND); // swaps out the first ring
        h.assertTrue(p.getMainHandItem().getItem() == com.vladosiwe.solostatus.registry.ModItems.RING.get(), "the swapped ring did not come back");
        h.assertTrue(d.gear(com.vladosiwe.solostatus.accessory.Affix.MAX_HEALTH) == 0, "a third ring was counted: " + d.gear);
        double crit = d.gear(com.vladosiwe.solostatus.accessory.Affix.CRIT_CHANCE);
        h.assertTrue(Math.abs(crit - com.vladosiwe.solostatus.accessory.Affix.CRIT_CHANCE.cap) < 1e-6, "crit total not capped at 8%: " + crit);

        com.vladosiwe.solostatus.accessory.AccessorySystem.unequip(p, 0);
        com.vladosiwe.solostatus.accessory.AccessorySystem.unequip(p, 1);
        h.assertTrue(d.gear.isEmpty(), "bonuses stayed after taking the rings off: " + d.gear);
        h.succeed();
    }

    /** Chests of any structure sometimes hold jewellery, already rolled. */
    @GameTest(template = "empty")
    public static void chestsHoldJewellery(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var table = server.reloadableRegistries().getLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON);
        int found = 0, rolled = 0;
        for (int i = 0; i < 400; i++) {
            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, h.absoluteVec(new net.minecraft.world.phys.Vec3(1, 1, 1)))
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
            for (ItemStack st : table.getRandomItems(params)) {
                if (st.getItem() instanceof com.vladosiwe.solostatus.accessory.AccessoryItem) {
                    found++;
                    if (st.get(com.vladosiwe.solostatus.registry.ModComponents.ACCESSORY.get()) != null) rolled++;
                }
            }
        }
        h.assertTrue(found > 5 && found < 120, "jewellery in 400 dungeon chests: " + found + " (expected about 48)");
        h.assertTrue(rolled == found, "unrolled jewellery in a chest");
        h.succeed();
    }

    /** With Curios installed, an amulet in its necklace slot counts like one in the System slot. */
    @GameTest(template = "empty")
    public static void curiosJewelleryCounts(GameTestHelper h) {
        if (!ModList.get().isLoaded("curios")) {
            if (required()) h.fail("Curios is not loaded");
            h.succeed();
            return;
        }
        h.assertTrue(com.vladosiwe.solostatus.accessory.CuriosAccess.ready(), "Curios API not reachable: " + com.vladosiwe.solostatus.accessory.CuriosAccess.error());
        ServerPlayer p = hunter(h, HunterClass.WARRIOR);
        PlayerData d = Hunter.get(p);
        boolean put = com.vladosiwe.solostatus.accessory.CuriosAccess.put(p, "necklace", 0, jewel(com.vladosiwe.solostatus.accessory.AccessoryKind.AMULET, "xp_gain", 0.05f));
        h.assertTrue(put, "could not put an amulet into the Curios necklace slot");
        com.vladosiwe.solostatus.accessory.AccessorySystem.refresh(p);
        h.assertTrue(d.gear(com.vladosiwe.solostatus.accessory.Affix.XP_GAIN) > 0.04, "Curios amulet not counted: " + d.gear
                + " (curios stacks: " + com.vladosiwe.solostatus.accessory.CuriosAccess.equipped(p) + ")");
        h.succeed();
    }

    /** Awakening scene: starts, protects the hunter, is remembered (plays once), ends after its time. */
    @GameTest(template = "empty")
    public static void awakeningScenePlaysOnce(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        PlayerData d = Hunter.get(p);
        h.assertTrue(!d.introPlayed, "a new profile has already seen the awakening");
        com.vladosiwe.solostatus.awakening.AwakeningScene.start(p);
        h.assertTrue(com.vladosiwe.solostatus.awakening.AwakeningScene.active(p), "the scene did not start");
        h.assertTrue(d.introPlayed, "the scene is not remembered");
        var res = p.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
        h.assertTrue(res != null && res.getAmplifier() >= 4, "the hunter is not protected while lying");
        PlayerData copy = d.copy();
        h.assertTrue(copy.introPlayed, "introPlayed is not saved");
        com.vladosiwe.solostatus.awakening.AwakeningScene.expireForTest(p);
        h.assertTrue(!com.vladosiwe.solostatus.awakening.AwakeningScene.active(p), "the scene did not end");
        h.succeed();
    }
}
