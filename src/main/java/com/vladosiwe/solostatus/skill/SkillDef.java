package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.clazz.Specialization;
import com.vladosiwe.solostatus.data.PlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Описание навыка. Неизменяемо после регистрации в {@link Skills}. */
public final class SkillDef {
    public final String id;
    public final SkillSource source;
    public final SkillCategory category;
    public final Item icon;
    public final int maxRank;
    @Nullable public final HunterClass hunterClass;
    @Nullable public final Specialization spec;
    /** Уровень персонажа для 1-го ранга; каждый следующий ранг требует +rankLevelStep уровней. */
    public final int baseLevel;
    public final int rankLevelStep;
    public final int cooldownTicks;
    public final int cooldownReductionPerRank;
    public final float energyCost;
    public final List<Requirement> requirements;
    @Nullable public final SkillEffect effect;
    /** Колонка/строка в дереве навыков интерфейса и родитель для линии связи. */
    public final int treeColumn;
    public final int treeRow;
    @Nullable public final String parent;

    private SkillDef(Builder b) {
        id = b.id; source = b.source; category = b.category; icon = b.icon; maxRank = b.maxRank;
        hunterClass = b.hunterClass; spec = b.spec; baseLevel = b.baseLevel; rankLevelStep = b.rankLevelStep;
        cooldownTicks = b.cooldownTicks; cooldownReductionPerRank = b.cooldownReductionPerRank; energyCost = b.energyCost;
        requirements = List.copyOf(b.requirements); effect = b.effect;
        treeColumn = b.treeColumn; treeRow = b.treeRow; parent = b.parent;
    }

    public boolean isActive() {
        return effect != null;
    }

    public boolean hidden() {
        return source == SkillSource.HIDDEN;
    }

    public Component name() {
        return Component.translatable("solostatus.skill." + id);
    }

    public Component description() {
        return Component.translatable("solostatus.skill." + id + ".desc");
    }

    /** Подсказка для скрытого навыка (без раскрытия условий). */
    public Component hint() {
        return Component.translatable("solostatus.skill." + id + ".hint");
    }

    public int cooldownFor(int rank) {
        return Math.max(10, cooldownTicks - cooldownReductionPerRank * (rank - 1));
    }

    public int levelForRank(int rank) {
        return baseLevel + rankLevelStep * (rank - 1);
    }

    /** Стоимость в очках навыков для получения ранга newRank. */
    public int costForRank(int newRank) {
        if (newRank == 1) {
            return switch (source) {
                case CLASS, GENERAL -> 1;
                case SPEC -> 2;
                default -> 0;
            };
        }
        if (source == SkillSource.ULTIMATE) return 2;
        return newRank <= 3 ? 1 : 2;
    }

    /** Подходит ли навык этому персонажу по классу/специализации. */
    public boolean availableFor(PlayerData d) {
        if (hunterClass != null && d.hunterClass != hunterClass) return false;
        return spec == null || d.specialization == spec;
    }

    public boolean requirementsMet(PlayerData d) {
        if (!availableFor(d)) return false;
        if (d.level < baseLevel) return false;
        for (Requirement r : requirements) if (!r.met(d)) return false;
        return true;
    }

    /** Средний прогресс условий (для полосы «прогресс открытия»). */
    public double unlockProgress(PlayerData d) {
        double sum = Math.min(1.0, d.level / (double) Math.max(1, baseLevel));
        for (Requirement r : requirements) sum += r.progress(d);
        return sum / (requirements.size() + 1);
    }

    public static Builder builder(String id, SkillSource source, SkillCategory category, Item icon) {
        return new Builder(id, source, category, icon);
    }

    public static final class Builder {
        private final String id;
        private final SkillSource source;
        private final SkillCategory category;
        private final Item icon;
        private int maxRank = 5;
        private HunterClass hunterClass;
        private Specialization spec;
        private int baseLevel = 1;
        private int rankLevelStep = 5;
        private int cooldownTicks = 0;
        private int cooldownReductionPerRank = 0;
        private float energyCost = 0;
        private final List<Requirement> requirements = new ArrayList<>();
        private SkillEffect effect;
        private int treeColumn, treeRow;
        private String parent;

        private Builder(String id, SkillSource source, SkillCategory category, Item icon) {
            this.id = id; this.source = source; this.category = category; this.icon = icon;
        }

        public Builder ranks(int max) { this.maxRank = max; return this; }
        public Builder forClass(HunterClass c) { this.hunterClass = c; return this; }
        public Builder forSpec(Specialization s) { this.spec = s; this.hunterClass = s.parent; return this; }
        public Builder level(int lvl) { this.baseLevel = lvl; return this; }
        public Builder rankStep(int step) { this.rankLevelStep = step; return this; }
        /** Перезарядка в секундах и уменьшение за ранг в секундах. */
        public Builder cooldown(double seconds, double perRank) {
            this.cooldownTicks = (int) Math.round(seconds * 20);
            this.cooldownReductionPerRank = (int) Math.round(perRank * 20);
            return this;
        }
        public Builder energy(float cost) { this.energyCost = cost; return this; }
        public Builder requires(Requirement r) { this.requirements.add(r); return this; }
        public Builder effect(SkillEffect e) { this.effect = e; return this; }
        public Builder tree(int column, int row) { this.treeColumn = column; this.treeRow = row; return this; }
        public Builder parent(String parentId, int rank) {
            this.parent = parentId;
            this.requirements.add(Req.skill(parentId, rank));
            return this;
        }
        public SkillDef build() { return new SkillDef(this); }
    }
}
