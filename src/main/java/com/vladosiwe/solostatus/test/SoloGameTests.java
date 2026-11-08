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
        h.succeed();
    }

    /**
     * Живой игрок на сервере выбирает класс (получает стартовые навыки), получает опыт и растёт в уровне. У тестового игрока нет клиента с модом,
     * поэтому заодно проверяется, что сервер не падает при отправке пакетов такому игроку
     * (так же ведут себя FakePlayer других модов).
     */
    @GameTest(template = "empty")
    public static void playerLevelsUp(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
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
}
