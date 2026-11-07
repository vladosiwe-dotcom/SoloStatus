package com.vladosiwe.solostatus.data;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.clazz.Specialization;
import com.vladosiwe.solostatus.rank.HunterRank;
import com.vladosiwe.solostatus.stats.Stat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Полный профиль охотника. Хранится как data attachment игрока (сохраняется вместе с игроком,
 * копируется при смерти). Этот же класс используется на клиенте как кэш синхронизированных данных.
 *
 * Все изменения выполняются только на сервере через системы (LevelSystem, SkillSystem и т.д.);
 * клиент лишь отправляет запросы, которые сервер проверяет.
 */
public final class PlayerData implements INBTSerializable<CompoundTag> {
    /** Версия формата сохранения. Увеличивайте при изменении структуры и добавляйте миграцию в {@link #migrate}. */
    public static final int DATA_VERSION = 1;
    public static final int MAX_LEVEL = 100;
    public static final int SKILL_SLOTS = 4;
    public static final int RECENT_KILLS_WINDOW = 30;

    // --- Уровень ---
    public int level = 1;
    public long xp = 0;
    /** Опыт сверх 100 уровня копится в «шкалу пробуждения» и даёт ограниченное число бонусных очков навыков. */
    public long overflowXp = 0;
    public int overflowAwakenings = 0;

    // --- Характеристики ---
    public final int[] stats = new int[Stat.VALUES.length];
    public int freeStatPoints = 0;

    // --- Класс ---
    @Nullable public HunterClass hunterClass;
    @Nullable public Specialization specialization;
    /** Пройдено Испытание класса (вторая ступень специализации). */
    public boolean advanced;

    // --- Навыки ---
    public int skillPoints = 0;
    /** id навыка -> ранг (1..maxRank). */
    public final Map<String, Integer> skills = new LinkedHashMap<>();
    /** Ранги, за которые были потрачены очки навыков (для корректного сброса). */
    public final Map<String, Integer> paidRanks = new HashMap<>();
    public final String[] slots = new String[SKILL_SLOTS];
    /** id навыка -> игровое время (gameTime), когда перезарядка закончится. */
    public final Map<String, Long> cooldowns = new HashMap<>();
    public float energy = 100;

    // --- Боевой опыт ---
    /** Опыт мастерства: ключ — тип оружия или "magic:<школа>". */
    public final Map<String, Double> mastery = new HashMap<>();
    /** Счётчики боевых действий (kills, blocks, perfect_guards, dodges, backstabs, crits...). */
    public final Map<String, Long> counters = new HashMap<>();
    /** Убийства по типу существ (для достижений и разнообразия). */
    public final Map<String, Integer> killsByType = new HashMap<>();
    public final Set<String> bossesKilled = new LinkedHashSet<>();
    public final Deque<String> recentKills = new ArrayDeque<>();

    // --- Достижения и ранг ---
    public final Set<String> achievements = new LinkedHashSet<>();
    public final Set<String> unseenAchievements = new LinkedHashSet<>();
    public final Set<String> unseenSkills = new LinkedHashSet<>();
    public HunterRank rank = HunterRank.E;
    public int rankScore = 0;

    // --- Настройки ---
    public boolean privateProfile = false;
    /** Временные состояния навыков: ключ -> gameTime окончания. */
    public final Map<String, Long> timedStates = new HashMap<>();

    // --- Не сохраняется ---
    public transient boolean dirty = true;
    /** Мелкие изменения (счётчики, опыт мастерства) — синхронизируются не чаще раза в секунду. */
    public transient boolean softDirty = false;
    public transient boolean unlockCheckPending = true;
    public transient long lastBlockTick = -1000;
    public transient long shieldRaisedTick = -1000;
    public transient long lastDamagedTick = -1000;
    public transient long lastCombatTick = -1000;
    public transient long lastComboTick = -1000;
    public transient int comboCount = 0;
    public transient String comboWeapon = "";

    public PlayerData() {
        Arrays.fill(stats, Stat.BASE_VALUE);
    }

    // ===================== Удобные методы =====================

    public int stat(Stat s) {
        return stats[s.ordinal()];
    }

    public boolean hasClass() {
        return hunterClass != null;
    }

    public int skillRank(String id) {
        return skills.getOrDefault(id, 0);
    }

    public boolean hasSkill(String id) {
        return skills.containsKey(id);
    }

    public long counter(String key) {
        return counters.getOrDefault(key, 0L);
    }

    public void addCounter(String key, long amount) {
        counters.merge(key, amount, Long::sum);
        unlockCheckPending = true;
        softDirty = true;
    }

    public double masteryXp(String key) {
        return mastery.getOrDefault(key, 0.0);
    }

    public boolean stateActive(String key, long gameTime) {
        Long end = timedStates.get(key);
        return end != null && end > gameTime;
    }

    public void markDirty() {
        dirty = true;
        unlockCheckPending = true;
    }

    // ===================== Сериализация =====================

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return write();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        read(tag);
    }

    public CompoundTag write() {
        CompoundTag t = new CompoundTag();
        t.putInt("v", DATA_VERSION);
        t.putInt("level", level);
        t.putLong("xp", xp);
        t.putLong("overflowXp", overflowXp);
        t.putInt("overflowAwakenings", overflowAwakenings);
        t.putIntArray("stats", stats.clone());
        t.putInt("freeStat", freeStatPoints);
        if (hunterClass != null) t.putString("class", hunterClass.key);
        if (specialization != null) t.putString("spec", specialization.key);
        t.putBoolean("advanced", advanced);

        t.putInt("sp", skillPoints);
        t.put("skills", writeIntMap(skills));
        t.put("paid", writeIntMap(paidRanks));
        ListTag slotList = new ListTag();
        for (String s : slots) slotList.add(StringTag.valueOf(s == null ? "" : s));
        t.put("slots", slotList);
        CompoundTag cd = new CompoundTag();
        cooldowns.forEach(cd::putLong);
        t.put("cooldowns", cd);
        t.putFloat("energy", energy);

        CompoundTag m = new CompoundTag();
        mastery.forEach(m::putDouble);
        t.put("mastery", m);
        CompoundTag c = new CompoundTag();
        counters.forEach(c::putLong);
        t.put("counters", c);
        t.put("killsByType", writeIntMap(killsByType));
        t.put("bosses", writeStrings(bossesKilled));
        t.put("recentKills", writeStrings(recentKills));

        t.put("achievements", writeStrings(achievements));
        t.put("unseenAch", writeStrings(unseenAchievements));
        t.put("unseenSkills", writeStrings(unseenSkills));
        t.putInt("rank", rank.ordinal());
        t.putInt("rankScore", rankScore);
        t.putBoolean("private", privateProfile);
        CompoundTag ts = new CompoundTag();
        timedStates.forEach(ts::putLong);
        t.put("timed", ts);
        return t;
    }

    public void read(CompoundTag t) {
        try {
            int version = t.contains("v") ? t.getInt("v") : 0;
            if (version > DATA_VERSION) {
                SoloStatus.LOGGER.warn("Профиль сохранён более новой версией мода ({} > {}), читаю совместимые поля", version, DATA_VERSION);
            }
            t = migrate(t, version);

            level = Mth.clamp(t.getInt("level"), 1, MAX_LEVEL);
            xp = Math.max(0, t.getLong("xp"));
            overflowXp = Math.max(0, t.getLong("overflowXp"));
            overflowAwakenings = Math.max(0, t.getInt("overflowAwakenings"));
            int[] arr = t.getIntArray("stats");
            Arrays.fill(stats, Stat.BASE_VALUE);
            for (int i = 0; i < Math.min(arr.length, stats.length); i++) stats[i] = Mth.clamp(arr[i], 1, 10000);
            freeStatPoints = Math.max(0, t.getInt("freeStat"));
            hunterClass = null;
            for (HunterClass hc : HunterClass.VALUES) if (hc.key.equals(t.getString("class"))) hunterClass = hc;
            specialization = null;
            for (Specialization sp : Specialization.VALUES) if (sp.key.equals(t.getString("spec"))) specialization = sp;
            if (specialization != null && specialization.parent != hunterClass) specialization = null;
            advanced = t.getBoolean("advanced") && specialization != null;

            skillPoints = Math.max(0, t.getInt("sp"));
            readIntMap(t.getCompound("skills"), skills);
            skills.replaceAll((k, v) -> Math.max(1, v));
            readIntMap(t.getCompound("paid"), paidRanks);
            Arrays.fill(slots, null);
            ListTag slotList = t.getList("slots", Tag.TAG_STRING);
            for (int i = 0; i < Math.min(slotList.size(), SKILL_SLOTS); i++) {
                String s = slotList.getString(i);
                slots[i] = s.isEmpty() ? null : s;
            }
            cooldowns.clear();
            CompoundTag cd = t.getCompound("cooldowns");
            for (String k : cd.getAllKeys()) cooldowns.put(k, cd.getLong(k));
            energy = Math.max(0, t.contains("energy") ? t.getFloat("energy") : 100);

            mastery.clear();
            CompoundTag m = t.getCompound("mastery");
            for (String k : m.getAllKeys()) mastery.put(k, Math.max(0, m.getDouble(k)));
            counters.clear();
            CompoundTag c = t.getCompound("counters");
            for (String k : c.getAllKeys()) counters.put(k, Math.max(0, c.getLong(k)));
            readIntMap(t.getCompound("killsByType"), killsByType);
            readStrings(t.getList("bosses", Tag.TAG_STRING), bossesKilled);
            recentKills.clear();
            ListTag rk = t.getList("recentKills", Tag.TAG_STRING);
            for (int i = 0; i < rk.size(); i++) recentKills.add(rk.getString(i));
            while (recentKills.size() > RECENT_KILLS_WINDOW) recentKills.pollFirst();

            readStrings(t.getList("achievements", Tag.TAG_STRING), achievements);
            readStrings(t.getList("unseenAch", Tag.TAG_STRING), unseenAchievements);
            readStrings(t.getList("unseenSkills", Tag.TAG_STRING), unseenSkills);
            rank = HunterRank.byIndex(t.getInt("rank"));
            rankScore = Math.max(0, t.getInt("rankScore"));
            privateProfile = t.getBoolean("private");
            timedStates.clear();
            CompoundTag ts = t.getCompound("timed");
            for (String k : ts.getAllKeys()) timedStates.put(k, ts.getLong(k));
        } catch (Exception e) {
            // Повреждённые данные не должны ронять сервер: оставляем то, что успели прочитать, остальное — по умолчанию.
            SoloStatus.LOGGER.error("Не удалось полностью прочитать профиль охотника, часть значений сброшена по умолчанию", e);
        }
        markDirty();
    }

    /** Миграция старых форматов. Версия 0 — профиль без поля версии (только значения по умолчанию). */
    private static CompoundTag migrate(CompoundTag t, int fromVersion) {
        // Пример будущей миграции:
        // if (fromVersion < 2) { t.putString("newField", convert(t.get("oldField"))); }
        return t;
    }

    /** Копия профиля (используется при клонировании игрока и для клиентского кэша). */
    public PlayerData copy() {
        PlayerData d = new PlayerData();
        d.read(write());
        return d;
    }

    private static CompoundTag writeIntMap(Map<String, Integer> map) {
        CompoundTag t = new CompoundTag();
        map.forEach(t::putInt);
        return t;
    }

    private static void readIntMap(CompoundTag t, Map<String, Integer> out) {
        out.clear();
        for (String k : t.getAllKeys()) out.put(k, Math.max(0, t.getInt(k)));
    }

    private static ListTag writeStrings(Collection<String> values) {
        ListTag l = new ListTag();
        for (String s : values) l.add(StringTag.valueOf(s));
        return l;
    }

    private static void readStrings(ListTag l, Collection<String> out) {
        out.clear();
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
    }
}
