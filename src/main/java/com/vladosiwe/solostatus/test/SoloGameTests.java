package com.vladosiwe.solostatus.test;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.achievement.AchievementDef;
import com.vladosiwe.solostatus.achievement.Achievements;
import com.vladosiwe.solostatus.clazz.ClassSystem;
import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.level.LevelSystem;
import com.vladosiwe.solostatus.level.XpReason;
import com.vladosiwe.solostatus.skill.SkillDef;
import com.vladosiwe.solostatus.skill.Skills;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Игровые тесты, которые GitHub запускает на настоящем выделенном сервере с модом (задача runGameTestServer).
 * В обычной игре они не работают: NeoForge включает их только для пространства имён из
 * neoforge.enabledGameTestNamespaces, а его выставляет лишь тестовый запуск.
 */
@GameTestHolder(SoloStatus.MODID)
@PrefixGameTestTemplate(false)
public final class SoloGameTests {
    private SoloGameTests() {}

    /** Реестры мода согласованы: у навыков существующие родители, награды достижений ссылаются на настоящие навыки. */
    @GameTest(template = "empty")
    public static void registriesAreConsistent(GameTestHelper h) {
        h.assertTrue(Skills.all().size() >= 90, "ожидалось не меньше 90 навыков, есть " + Skills.all().size());
        for (SkillDef s : Skills.all()) {
            if (s.parent != null) h.assertTrue(Skills.get(s.parent) != null, "навык " + s.id + ": нет родителя " + s.parent);
        }
        for (AchievementDef a : Achievements.all()) {
            if (a.rewardSkill() != null) h.assertTrue(Skills.get(a.rewardSkill()) != null, "достижение " + a.id() + ": нет навыка " + a.rewardSkill());
        }
        // every System skill has its glyph and a name and description in both languages
        java.util.List<String> missing = new java.util.ArrayList<>();
        java.util.Map<String, com.google.gson.JsonObject> langs = new java.util.LinkedHashMap<>();
        for (String lang : new String[]{"ru_ru", "en_us"}) {
            try (var in = SoloGameTests.class.getResourceAsStream("/assets/solostatus/lang/" + lang + ".json")) {
                langs.put(lang, com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                missing.add(lang + ".json: " + e);
            }
        }
        for (SkillDef s : Skills.all()) {
            if (s.isSpell()) continue; // Iron's own icon and names
            if (SoloGameTests.class.getResource("/assets/solostatus/textures/gui/skill/" + s.id + ".png") == null) missing.add(s.id + ".png");
            langs.forEach((lang, json) -> {
                if (!json.has("solostatus.skill." + s.id) || !json.has("solostatus.skill." + s.id + ".desc")) missing.add(lang + ": " + s.id);
            });
        }
        for (String id : com.vladosiwe.solostatus.skill.SkillMods.SKILLS) {
            for (int i = 1; i <= 2; i++) {
                String k = "solostatus.mod." + id + "." + i;
                langs.forEach((lang, json) -> { if (!json.has(k) || !json.has(k + ".desc")) missing.add(lang + ": " + k); });
            }
        }
        for (var t : com.vladosiwe.solostatus.combat.WeaponType.VALUES) {
            if (!t.hasMastery()) continue;
            langs.forEach((lang, json) -> {
                if (!json.has("solostatus.mastery.technique." + t.key) || !json.has("solostatus.mastery.mastered." + t.key)) missing.add(lang + ": mastery " + t.key);
            });
        }
        h.assertTrue(missing.isEmpty(), "missing: " + missing);
        h.succeed();
    }

    /** Кривая опыта растёт с уровнем и не переполняется. */
    @GameTest(template = "empty")
    public static void xpCurveGrows(GameTestHelper h) {
        long prev = 0;
        for (int lv = 1; lv < 100; lv++) {
            long need = LevelSystem.xpToNext(lv);
            h.assertTrue(need > prev, "опыт до уровня " + (lv + 1) + " не больше предыдущего: " + need);
            prev = need;
        }
        long total = 0;
        int points = 1; // the starting point
        for (int lv = 1; lv < 100; lv++) {
            total += LevelSystem.xpToNext(lv);
            points += LevelSystem.skillPointsForLevel(lv + 1);
        }
        h.assertTrue(total > 650_000 && total < 850_000, "experience to level 100 is " + total + ", the pace targets need ≈750 thousand");
        h.assertTrue(points == 70, "skill points by level 100: " + points + ", expected 70");
        h.succeed();
    }

    /** A profile from before version 3 gets the skill points of the odd levels below 20 it already passed. */
    @GameTest(template = "empty")
    public static void skillPointCatchUp(GameTestHelper h) {
        PlayerData old = new PlayerData();
        old.level = 15;
        old.skillPoints = 4;
        var tag = old.write();
        tag.putInt("v", 2);
        PlayerData d = new PlayerData();
        d.read(tag);
        h.assertTrue(d.skillPoints == 4 + 7, "skill points " + d.skillPoints + ", expected 11 (levels 3, 5 ... 15)");
        h.succeed();
    }

    /**
     * Живой игрок на сервере выбирает класс (получает стартовые навыки), получает опыт и растёт в уровне. У тестового игрока нет клиента с модом,
     * поэтому заодно проверяется, что сервер не падает при отправке пакетов такому игроку
     * (так же ведут себя FakePlayer других модов).
     */
    @GameTest(template = "empty")
    public static void playerLevelsUp(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        // опыт идёт только после пробуждения Системы — выбора класса
        ClassSystem.chooseClass(p, HunterClass.WARRIOR);
        PlayerData d = Hunter.get(p);
        h.assertTrue(d.hasClass(), "класс не выбран");
        h.assertTrue(!d.skills.isEmpty(), "класс не выдал стартовые навыки");
        int before = d.level;
        LevelSystem.grantXp(p, LevelSystem.xpToNext(before) * 3.0, XpReason.COMMAND);
        h.assertTrue(Hunter.get(p).level > before, "уровень не вырос: " + before + " → " + Hunter.get(p).level);
        h.succeed();
    }

    /** A class handles its own weapons better: a Warrior swings a dagger 25% slower and a sword 10% faster. */
    @GameTest(template = "empty")
    public static void classWeaponProficiency(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ClassSystem.chooseClass(p, HunterClass.WARRIOR);
        var id = com.vladosiwe.solostatus.SoloStatus.id("class_weapon_speed");
        var speed = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);

        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        com.vladosiwe.solostatus.stats.AttributeSystem.applyWeaponSpeed(p);
        var mod = speed.getModifier(id);
        h.assertTrue(mod != null && Math.abs(mod.amount() - 0.10) < 1e-6, "a sword is not the Warrior's main weapon: " + mod);

        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        com.vladosiwe.solostatus.stats.AttributeSystem.applyWeaponSpeed(p);
        h.assertTrue(speed.getModifier(id) == null, "an empty hand must have no class speed modifier");

        h.assertTrue(com.vladosiwe.solostatus.clazz.Proficiency.of(HunterClass.WARRIOR, com.vladosiwe.solostatus.combat.WeaponType.DAGGER)
                == com.vladosiwe.solostatus.clazz.Proficiency.FOREIGN, "a dagger must be foreign to the Warrior");
        h.assertTrue(com.vladosiwe.solostatus.clazz.Proficiency.of(HunterClass.THIEF, com.vladosiwe.solostatus.combat.WeaponType.DAGGER)
                == com.vladosiwe.solostatus.clazz.Proficiency.MAIN, "a dagger must be the Thief's main weapon");
        h.succeed();
    }

    /** Slayer titles: kills of one kind are counted, the title opens, and while worn it hits that kind harder. */
    @GameTest(template = "empty")
    public static void slayerTitles(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ClassSystem.chooseClass(p, HunterClass.WARRIOR);
        PlayerData d = Hunter.get(p);
        var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, net.minecraft.core.BlockPos.ZERO.above());
        var spider = h.spawn(net.minecraft.world.entity.EntityType.SPIDER, net.minecraft.core.BlockPos.ZERO.above());
        com.vladosiwe.solostatus.achievement.SlayerTitles.onKill(d, zombie);
        h.assertTrue(d.counter("slay_zombie_slayer") == 1, "a zombie kill is not counted for the Corpse Slayer title");
        h.assertTrue(com.vladosiwe.solostatus.achievement.Achievements.get("zombie_slayer") != null, "zombie_slayer is not a title");
        d.achievements.add("zombie_slayer");
        com.vladosiwe.solostatus.achievement.TitleSystem.equip(p, "zombie_slayer");
        h.assertTrue(d.titleIs("zombie_slayer"), "the title was not equipped");
        h.assertTrue(Math.abs(com.vladosiwe.solostatus.achievement.SlayerTitles.damageBonus(d, zombie) - 0.12) < 1e-9, "no damage bonus against zombies");
        h.assertTrue(com.vladosiwe.solostatus.achievement.SlayerTitles.damageBonus(d, spider) == 0, "the zombie title must not help against spiders");
        h.succeed();
    }

    /** Every class has three specializations, each with an active and a passive skill. */
    @GameTest(template = "empty")
    public static void threeSpecializationsPerClass(GameTestHelper h) {
        for (HunterClass c : HunterClass.VALUES) {
            var specs = c.specializations();
            h.assertTrue(specs.length == 3, c + " has " + specs.length + " specializations");
            for (var s : specs) {
                var skills = com.vladosiwe.solostatus.skill.Skills.forSpec(s);
                h.assertTrue(skills.stream().anyMatch(d -> d.isActive()) && skills.stream().anyMatch(d -> !d.isActive()),
                        s + " needs an active and a passive skill");
            }
        }
        h.succeed();
    }

    /** Crossbowman's Auto-reload winds an empty crossbow and takes one arrow from the inventory. */
    @GameTest(template = "empty")
    public static void crossbowAutoReload(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ClassSystem.chooseClass(p, HunterClass.ARCHER);
        PlayerData d = Hunter.get(p);
        d.level = 20;
        d.specialization = com.vladosiwe.solostatus.clazz.Specialization.CROSSBOWMAN;
        d.skills.put("auto_reload", 1);
        var crossbow = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CROSSBOW);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, crossbow);
        p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW, 5));
        com.vladosiwe.solostatus.combat.CrossbowReload.reload(p);
        h.assertTrue(net.minecraft.world.item.CrossbowItem.isCharged(p.getMainHandItem()), "the crossbow was not reloaded");
        h.assertTrue(p.getInventory().countItem(net.minecraft.world.item.Items.ARROW) == 4, "the reload did not take one arrow");
        h.succeed();
    }

    /** Avenger: damage taken is stored, and Retribution gives it back and empties the store. */
    @GameTest(template = "empty")
    public static void avengerStoresDamage(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ClassSystem.chooseClass(p, HunterClass.DEFENDER);
        PlayerData d = Hunter.get(p);
        d.level = 20;
        d.specialization = com.vladosiwe.solostatus.clazz.Specialization.AVENGER;
        d.skills.put("vengeance", 3);
        d.skills.put("retribution", 1);
        var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, net.minecraft.core.BlockPos.ZERO.above());
        // a FakePlayer is invulnerable, so the incoming-damage event the System listens to is posted directly
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(p,
                new net.neoforged.neoforge.common.damagesource.DamageContainer(p.damageSources().mobAttack(zombie), 8f)));
        h.assertTrue(d.vengeance > 0, "taken damage was not stored");
        var def = com.vladosiwe.solostatus.skill.Skills.get("retribution");
        boolean ok = def.effect.execute(new com.vladosiwe.solostatus.skill.SkillContext(p, p.serverLevel(), d, def, 1));
        h.assertTrue(ok && d.vengeance == 0, "Retribution did not release the stored damage");
        h.succeed();
    }

    /** Riposte works only in the moment after a successful parry. */
    @GameTest(template = "empty")
    public static void riposteOnlyAfterParry(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ClassSystem.chooseClass(p, HunterClass.WARRIOR);
        PlayerData d = Hunter.get(p);
        d.skills.put("riposte", 1);
        var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 1, 3));
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, zombie.getEyePosition());
        var def = com.vladosiwe.solostatus.skill.Skills.get("riposte");
        var ctx = new com.vladosiwe.solostatus.skill.SkillContext(p, p.serverLevel(), d, def, 1);
        h.assertFalse(def.effect.execute(ctx), "Riposte worked without a parry");
        com.vladosiwe.solostatus.skill.SkillFx.state(d, p.serverLevel(), "riposte_ready", 50);
        h.assertTrue(def.effect.execute(ctx), "Riposte did not work right after a parry");
        h.assertFalse(d.stateActive("riposte_ready", p.serverLevel().getGameTime()), "the riposte window must close after use");
        h.succeed();
    }

    /** Concealment: a mob drops the hunter as its target and cannot pick him again; the first blow ends it. */
    @GameTest(template = "empty")
    public static void concealmentLosesTargets(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ClassSystem.chooseClass(p, HunterClass.THIEF);
        var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 1, 3));
        // a freshly placed test area may not be entity-ticking yet, and entity searches skip it until it is
        h.runAfterDelay(5, () -> concealmentChecks(h, p, zombie));
    }

    private static void concealmentChecks(GameTestHelper h, ServerPlayer p, net.minecraft.world.entity.monster.Zombie zombie) {
        zombie.setTarget(p);
        com.vladosiwe.solostatus.skill.Concealment.start(p, 100);
        h.assertTrue(zombie.getTarget() == null, "the zombie still targets the concealed hunter");
        zombie.setTarget(p);
        h.assertTrue(zombie.getTarget() == null, "the zombie picked the concealed hunter again");
        com.vladosiwe.solostatus.skill.Concealment.end(p);
        h.assertFalse(com.vladosiwe.solostatus.skill.Concealment.active(p), "Concealment did not end");
        h.succeed();
    }

    /** AGI speeds up the weapon's own swing by a share of it: a greatsword gains as much in percent as a sword. */
    @GameTest(template = "empty")
    public static void agilityScalesWeaponSpeed(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ClassSystem.chooseClass(p, HunterClass.WARRIOR);
        PlayerData d = Hunter.get(p);
        d.stats[com.vladosiwe.solostatus.stats.Stat.AGI.ordinal()] = com.vladosiwe.solostatus.stats.Stat.BASE_VALUE + 40; // +16%
        var sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        // the sword's own speed is applied when the player ticks; a test player does not, so it is applied here
        sword.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attr, mod) -> p.getAttribute(attr).addOrUpdateTransientModifier(mod));
        com.vladosiwe.solostatus.stats.AttributeSystem.apply(p);
        double expected = 1.6 * 1.16 * 1.10; // sword 1.6, AGI +16%, a sword is familiar to the Warrior (+10%)
        double got = p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        h.assertTrue(Math.abs(got - expected) < 0.01, "attack speed " + got + ", expected " + expected);
        h.succeed();
    }

    /** Mage and Support spend Iron's mana, so the "mana / stamina" bonuses raise it. */
    @GameTest(template = "empty")
    public static void casterManaBonuses(GameTestHelper h) {
        if (!com.vladosiwe.solostatus.compat.CompatManager.ironsLoaded()) { h.succeed(); return; }
        ServerPlayer p = TestPlayers.create(h);
        ClassSystem.chooseClass(p, HunterClass.MAGE);
        PlayerData d = Hunter.get(p);
        double before = com.vladosiwe.solostatus.compat.ironsspells.IronsSpellsBridge.maxMana(p);
        d.gear.put(com.vladosiwe.solostatus.accessory.Affix.MAX_ENERGY, 30.0);
        com.vladosiwe.solostatus.stats.AttributeSystem.apply(p);
        double after = com.vladosiwe.solostatus.compat.ironsspells.IronsSpellsBridge.maxMana(p);
        h.assertTrue(Math.abs(after - before - 30) < 0.5, "max mana " + before + " -> " + after + ", expected +30");
        h.succeed();
    }

    /** A profile saved before version 2 loses the retired Mage skills and gets the paid points back. */
    @GameTest(template = "empty")
    public static void oldProfileMigrates(GameTestHelper h) {
        PlayerData old = new PlayerData();
        old.level = 12;
        old.hunterClass = HunterClass.MAGE;
        old.skillPoints = 2;
        old.skills.put("mana_bolt", 3);
        old.paidRanks.put("mana_bolt", 2);
        old.skills.put("frost_nova", 1); // no paid record: the old table gives 1 back
        old.slots[0] = "mana_bolt";
        old.skills.put("arcane_mind", 2);
        var tag = old.write();
        tag.putInt("v", 1);
        PlayerData d = new PlayerData();
        d.read(tag);
        h.assertTrue(d.level == 12 && d.hunterClass == HunterClass.MAGE, "level or class lost");
        h.assertFalse(d.skills.containsKey("mana_bolt") || d.skills.containsKey("frost_nova"), "retired skills were kept");
        h.assertTrue(d.skillRank("arcane_mind") == 2, "a current skill was lost");
        // 2 kept + 2 paid for Mana Bolt + 1 for Frost Nova + 5 for the odd levels 3..11 (version 3)
        h.assertTrue(d.skillPoints == 2 + 2 + 1 + 5, "skill points " + d.skillPoints + ", expected 10");
        h.assertTrue(d.slots[0] == null, "the retired skill stayed in a slot");
        h.assertTrue(d.skillsMigrated, "the player is not told about the change");
        PlayerData again = new PlayerData();
        again.read(d.write());
        h.assertTrue(again.skillPoints == 10 && !again.skillsMigrated, "the migration ran twice");
        h.succeed();
    }

    /**
     * Every class can use its costliest skill at least twice from a full pool, at levels 10, 40, 70 and 100 with the
     * stat points spread evenly, and a full stamina pool comes back within about a minute (75 s at most).
     */
    @GameTest(template = "empty")
    public static void resourceCoversSkills(GameTestHelper h) {
        java.util.List<String> wrong = new java.util.ArrayList<>();
        for (HunterClass hc : HunterClass.values()) {
            for (int level : new int[]{10, 40, 70, 100}) {
                ServerPlayer p = TestPlayers.create(h);
                ClassSystem.chooseClass(p, hc);
                PlayerData d = Hunter.get(p);
                d.level = level;
                for (var st : com.vladosiwe.solostatus.stats.Stat.values()) d.stats[st.ordinal()] = com.vladosiwe.solostatus.stats.Stat.BASE_VALUE + level / 2;
                com.vladosiwe.solostatus.stats.AttributeSystem.apply(p);
                float pool = com.vladosiwe.solostatus.skill.ManaPool.max(p, d);
                float costliest = 0;
                String which = "";
                for (SkillDef s : Skills.all()) {
                    boolean mine = s.hunterClass == hc || (s.spec != null && s.spec.parent == hc) || s.source == com.vladosiwe.solostatus.skill.SkillSource.GENERAL;
                    if (mine && s.baseLevel <= level && s.ironsSpell == null && s.energyCost > costliest) { costliest = s.energyCost; which = s.id; }
                }
                if (pool < costliest * 2) wrong.add(hc.key + " lv" + level + ": pool " + pool + " < 2 × " + which + " " + costliest);
                if (!com.vladosiwe.solostatus.skill.ManaPool.ironsMana(d)) {
                    double refill = pool / com.vladosiwe.solostatus.stats.StatFormulas.energyRegen(d);
                    if (refill > 75) wrong.add(hc.key + " lv" + level + ": stamina refills in " + Math.round(refill) + " s");
                }
            }
        }
        h.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
        h.succeed();
    }

    /** Every specialization opens a development of its main skill at 30 and a style passive at 60, in one chain. */
    @GameTest(template = "empty")
    public static void specializationLaterSteps(GameTestHelper h) {
        for (var spec : com.vladosiwe.solostatus.clazz.Specialization.values()) {
            var defs = Skills.forSpec(spec);
            SkillDef at30 = defs.stream().filter(x -> x.baseLevel == 30).findFirst().orElse(null);
            SkillDef at60 = defs.stream().filter(x -> x.baseLevel == 60).findFirst().orElse(null);
            h.assertTrue(at30 != null && at60 != null, spec.key + ": no level 30 / 60 step");
            SkillDef main = Skills.get(at30.parent);
            h.assertTrue(main != null && main.isActive() && main.spec == spec, spec.key + ": the level 30 step must grow from the main skill");
            h.assertTrue(at30.id.equals(at60.parent), spec.key + ": the level 60 step must follow the level 30 one");
        }
        h.succeed();
    }

    /** A few of the later steps, checked through their numbers. */
    @GameTest(template = "empty")
    public static void styleSkillEffects(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ClassSystem.chooseClass(p, HunterClass.DEFENDER);
        PlayerData d = Hunter.get(p);
        long now = p.serverLevel().getGameTime();

        float before = p.getMaxHealth();
        d.skills.put("colossus", 2);
        com.vladosiwe.solostatus.stats.AttributeSystem.apply(p);
        h.assertTrue(Math.abs(p.getMaxHealth() - before * 1.10f) < 0.05f, "Colossus 2: max health " + before + " -> " + p.getMaxHealth());

        d.skills.put("song_of_valor", 3);
        h.assertTrue(Math.abs(com.vladosiwe.solostatus.skill.StyleSkills.defense(p, d, null, now) - 1.0) < 1e-6, "Song of Valor works without the hymn");
        com.vladosiwe.solostatus.skill.SkillFx.state(d, p.serverLevel(), "battle_hymn", 100);
        h.assertTrue(Math.abs(com.vladosiwe.solostatus.skill.StyleSkills.defense(p, d, null, now) - 0.85) < 1e-6, "Song of Valor 3 must cut damage by 15%");

        d.skills.put("vengeance", 1);
        d.skills.put("eye_for_eye", 2);
        h.assertTrue(Math.abs(com.vladosiwe.solostatus.skill.StyleSkills.vengeanceShare(d, 1) - 0.45) < 1e-6
                && com.vladosiwe.solostatus.skill.StyleSkills.vengeanceCap(d, 1) == 46, "Eye for an Eye numbers");

        d.skills.put("parry", 1);
        d.skills.put("en_garde", 3);
        var parry = Skills.get("parry");
        parry.effect.execute(new com.vladosiwe.solostatus.skill.SkillContext(p, p.serverLevel(), d, parry, 1));
        long window = d.timedStates.get("parry") - p.serverLevel().getGameTime();
        h.assertTrue(window == com.vladosiwe.solostatus.skill.GeneralSkills.parryWindow(1) + 6, "En Garde 3: parry window " + window);
        h.succeed();
    }

    /** Mastery 40 gives each weapon its technique and 50 develops its skill; both are described for every weapon. */
    @GameTest(template = "empty")
    public static void masteryTechniques(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ClassSystem.chooseClass(p, HunterClass.THIEF);
        PlayerData d = Hunter.get(p);
        var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 1, 3));
        zombie.setHealth(zombie.getMaxHealth() * 0.2f);
        var dagger = com.vladosiwe.solostatus.combat.WeaponType.DAGGER;
        h.assertTrue(com.vladosiwe.solostatus.mastery.MasteryTechniques.offense(p, d, zombie, dagger, p, 1) == 0, "a technique before mastery 40");
        d.mastery.put(dagger.key, com.vladosiwe.solostatus.mastery.MasteryMath.xpForLevel(40));
        h.assertTrue(Math.abs(com.vladosiwe.solostatus.mastery.MasteryTechniques.offense(p, d, zombie, dagger, p, 1) - 0.25) < 1e-9,
                "the dagger technique must add 25% against a wounded target");
        h.assertFalse(com.vladosiwe.solostatus.mastery.MasteryTechniques.mastered(d, dagger), "mastered before 50");
        d.mastery.put(dagger.key, com.vladosiwe.solostatus.mastery.MasteryMath.xpForLevel(50));
        h.assertTrue(com.vladosiwe.solostatus.mastery.MasteryTechniques.mastered(d, dagger), "not mastered at 50");
        h.succeed();
    }

    /** Skill modifications: open at rank 3, the first pick is free, a change needs a Rebirth Orb, a skill reset clears them. */
    @GameTest(template = "empty")
    public static void skillModifications(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ClassSystem.chooseClass(p, HunterClass.WARRIOR);
        PlayerData d = Hunter.get(p);
        d.skills.put("whirlwind", 2);
        com.vladosiwe.solostatus.skill.SkillMods.choose(p, "whirlwind", 1);
        h.assertTrue(com.vladosiwe.solostatus.skill.SkillMods.of(d, "whirlwind") == 0, "a modification was chosen below rank 3");
        d.skills.put("whirlwind", 3);
        com.vladosiwe.solostatus.skill.SkillMods.choose(p, "whirlwind", 1);
        h.assertTrue(com.vladosiwe.solostatus.skill.SkillMods.of(d, "whirlwind") == 1, "the first pick must be free");
        com.vladosiwe.solostatus.skill.SkillMods.choose(p, "whirlwind", 2);
        h.assertTrue(com.vladosiwe.solostatus.skill.SkillMods.of(d, "whirlwind") == 1, "changed without a Rebirth Orb");
        p.getInventory().add(new net.minecraft.world.item.ItemStack(com.vladosiwe.solostatus.registry.ModItems.RESET_ORB.get()));
        com.vladosiwe.solostatus.skill.SkillMods.choose(p, "whirlwind", 2);
        h.assertTrue(com.vladosiwe.solostatus.skill.SkillMods.of(d, "whirlwind") == 2, "the orb did not allow the change");
        h.assertTrue(p.getInventory().countItem(com.vladosiwe.solostatus.registry.ModItems.RESET_ORB.get()) == 0, "the orb was not spent");
        com.vladosiwe.solostatus.skill.SkillMods.choose(p, "power_strike", 1);
        h.assertFalse(d.skillMods.containsKey("power_strike"), "a skill without modifications got one");
        PlayerData copy = new PlayerData();
        copy.read(d.write());
        h.assertTrue(copy.skillMods.getOrDefault("whirlwind", 0) == 2, "the modification was not saved");
        for (String id : com.vladosiwe.solostatus.skill.SkillMods.SKILLS) {
            h.assertTrue(Skills.get(id) != null && Skills.get(id).isActive() && Skills.get(id).maxRank >= com.vladosiwe.solostatus.skill.SkillMods.RANK,
                    id + " cannot carry a modification");
        }
        h.succeed();
    }
}
