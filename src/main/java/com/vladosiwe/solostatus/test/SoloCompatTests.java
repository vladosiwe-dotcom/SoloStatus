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
}
