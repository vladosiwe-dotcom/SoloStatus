package com.vladosiwe.solostatus.skill;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.network.Notices;
import com.vladosiwe.solostatus.registry.ModItems;
import com.vladosiwe.solostatus.stats.AttributeSystem;
import com.vladosiwe.solostatus.stats.Stat;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * Серверная логика навыков: изучение и улучшение за очки, назначение в слоты, применение
 * (проверка перезарядки, энергии и условий), перераспределение через Сферу перерождения.
 */
public final class SkillSystem {
    private SkillSystem() {}

    // ============================ Изучение ============================

    /** Проверка: можно ли получить следующий ранг. Возвращает ключ ошибки или null. */
    public static String canAdvance(PlayerData d, SkillDef def) {
        int current = d.skillRank(def.id);
        if (current >= def.maxRank) return "solostatus.error.max_rank";
        if (!def.availableFor(d)) return "solostatus.error.wrong_class";
        int next = current + 1;
        if (current == 0) {
            if (def.source.autoAwaken() || def.source == SkillSource.CLASS_START) return "solostatus.error.awaken_only";
            if (!def.requirementsMet(d)) return "solostatus.error.requirements";
        }
        if (d.level < def.levelForRank(next)) return "solostatus.error.level";
        if (d.skillPoints < def.costForRank(next)) return "solostatus.error.no_points";
        return null;
    }

    public static void learnOrUpgrade(ServerPlayer player, String id) {
        PlayerData d = Hunter.get(player);
        SkillDef def = Skills.get(id);
        if (def == null) return;
        String error = canAdvance(d, def);
        if (error != null) {
            Notices.warn(player, error);
            return;
        }
        int next = d.skillRank(id) + 1;
        int cost = def.costForRank(next);
        d.skillPoints -= cost;
        d.skills.put(id, next);
        d.paidRanks.merge(id, cost, Integer::sum);
        if (next == 1) autoEquip(d, def);
        AttributeSystem.apply(player);
        d.markDirty();
        player.serverLevel().sendParticles(SkillFx.SYSTEM_DUST, player.getX(), player.getY() + 1, player.getZ(), 25, 0.4, 0.6, 0.4, 0.02);
        SkillFx.sound(player.serverLevel(), player.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 0.8f, 1.4f);
    }

    /** Выдать навык бесплатно (стартовые и пробуждённые). */
    public static void grant(ServerPlayer player, PlayerData d, SkillDef def) {
        if (d.hasSkill(def.id)) return;
        d.skills.put(def.id, 1);
        d.unseenSkills.add(def.id);
        autoEquip(d, def);
        AttributeSystem.apply(player);
        d.markDirty();
    }

    private static void autoEquip(PlayerData d, SkillDef def) {
        if (!def.isActive()) return;
        for (String s : d.slots) if (def.id.equals(s)) return;
        for (int i = 0; i < d.slots.length; i++) {
            if (d.slots[i] == null) {
                d.slots[i] = def.id;
                return;
            }
        }
    }

    public static void equip(ServerPlayer player, int slot, String id) {
        PlayerData d = Hunter.get(player);
        if (slot < 0 || slot >= PlayerData.SKILL_SLOTS) return;
        if (id.isEmpty()) {
            d.slots[slot] = null;
            d.markDirty();
            return;
        }
        SkillDef def = Skills.get(id);
        if (def == null || !def.isActive() || !d.hasSkill(id) || !def.availableFor(d)) return;
        for (int i = 0; i < d.slots.length; i++) if (id.equals(d.slots[i])) d.slots[i] = null;
        d.slots[slot] = id;
        d.markDirty();
    }

    // ============================ Применение ============================

    public static void cast(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= PlayerData.SKILL_SLOTS || player.isSpectator() || !player.isAlive()) return;
        PlayerData d = Hunter.get(player);
        String id = d.slots[slot];
        SkillDef def = Skills.get(id);
        if (def == null || !def.isActive()) return;
        int rank = d.skillRank(id);
        if (rank <= 0 || !def.availableFor(d)) return;

        long now = player.serverLevel().getGameTime();
        Long ready = d.cooldowns.get(id);
        if (ready != null && ready > now) return; // клиент тоже показывает перезарядку — без спама уведомлениями
        float cost = energyCost(d, def);
        if (!player.isCreative() && d.energy < cost) {
            Notices.warn(player, StatFormulas.usesMana(d) ? "solostatus.error.no_mana" : "solostatus.error.no_stamina");
            return;
        }

        boolean ok;
        d.castingSkill = id;
        try {
            ok = def.effect.execute(new SkillContext(player, player.serverLevel(), d, def, rank));
            // spells and buffs do not swing: play the animation once the skill has worked
            if (ok && d.animatedTick != now && com.vladosiwe.solostatus.compat.CompatManager.playSkillAnimation(player, id)) d.animatedTick = now;
        } catch (Exception e) {
            SoloStatus.LOGGER.error("Ошибка навыка {}", id, e);
            ok = false;
        } finally {
            d.castingSkill = null;
        }
        if (!ok) {
            Notices.warn(player, "solostatus.error.no_target");
            return;
        }
        if (!player.isCreative()) d.energy -= cost;
        d.cooldowns.put(id, now + cooldownFor(d, def, rank));
        d.addCounter("skills_used", 1);
        d.markDirty();
        Notices.syncEnergy(player, d);
    }

    public static float energyCost(PlayerData d, SkillDef def) {
        return (float) (def.energyCost * (1 - d.skillRank("arcane_flow") * 0.06));
    }

    public static int cooldownFor(PlayerData d, SkillDef def, int rank) {
        return def.cooldownFor(rank);
    }

    // ============================ Перераспределение ============================

    /**
     * mode 0 — навыки: возвращаются все очки, потраченные на изучение и улучшения; пробуждённые и стартовые
     * навыки остаются на 1 ранге (они получены опытом, а не куплены).
     * mode 1 — характеристики: все вложенные очки возвращаются, бонус класса сохраняется.
     * Требует 1 Сферу перерождения; подтверждение — в интерфейсе.
     */
    public static void respec(ServerPlayer player, int mode) {
        PlayerData d = Hunter.get(player);
        if (!d.hasClass()) return;
        if (!player.isCreative() && !consumeOrb(player)) {
            Notices.warn(player, "solostatus.error.no_orb");
            return;
        }
        if (mode == 0) {
            int refund = 0;
            for (var e : d.paidRanks.entrySet()) refund += e.getValue();
            d.paidRanks.clear();
            d.skills.entrySet().removeIf(e -> {
                SkillDef def = Skills.get(e.getKey());
                return def == null || def.source == SkillSource.CLASS || def.source == SkillSource.SPEC || def.source == SkillSource.GENERAL;
            });
            d.skills.replaceAll((k, v) -> 1);
            for (int i = 0; i < d.slots.length; i++) if (d.slots[i] != null && !d.hasSkill(d.slots[i])) d.slots[i] = null;
            d.skillPoints += refund;
            d.cooldowns.clear();
        } else {
            int invested = 0;
            for (Stat s : Stat.VALUES) invested += d.stats[s.ordinal()] - Stat.BASE_VALUE;
            Arrays.fill(d.stats, Stat.BASE_VALUE);
            int classBonus = 0;
            if (d.hunterClass != null) {
                d.stats[d.hunterClass.primary.ordinal()] += 3;
                d.stats[d.hunterClass.secondary.ordinal()] += 2;
                classBonus = 5;
            }
            d.freeStatPoints += Math.max(0, invested - classBonus);
        }
        d.addCounter("respecs", 1);
        AttributeSystem.apply(player);
        d.energy = Math.min(d.energy, (float) StatFormulas.maxEnergy(d));
        d.markDirty();
        player.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 80, 0.5, 1, 0.5, 0.1);
        SkillFx.sound(player.serverLevel(), player.position(), SoundEvents.BEACON_DEACTIVATE, 1f, 1.2f);
        Notices.send(player, Notices.INFO, net.minecraft.network.chat.Component.translatable("solostatus.notice.respec"),
                net.minecraft.network.chat.Component.translatable(mode == 0 ? "solostatus.notice.respec.skills" : "solostatus.notice.respec.stats"));
    }

    private static boolean consumeOrb(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.RESET_ORB.get())) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }
}
