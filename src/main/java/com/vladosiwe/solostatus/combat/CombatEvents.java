package com.vladosiwe.solostatus.combat;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.clazz.Specialization;
import com.vladosiwe.solostatus.compat.CompatManager;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.level.LevelSystem;
import com.vladosiwe.solostatus.mastery.MasteryMath;
import com.vladosiwe.solostatus.mastery.MasterySystem;
import com.vladosiwe.solostatus.quality.QualitySystem;
import com.vladosiwe.solostatus.quality.WeaponQuality;
import com.vladosiwe.solostatus.skill.Scheduler;
import com.vladosiwe.solostatus.skill.SkillFx;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Событийный анализ боя. Никаких проверок каждый кадр: всё считается в момент урона, блока или убийства.
 * Работает с любыми атаками, проходящими через стандартные события урона NeoForge — в том числе с
 * атаками Epic Fight и заклинаниями Iron's Spells.
 */
@EventBusSubscriber(modid = SoloStatus.MODID)
public final class CombatEvents {
    /** Метка призванных навыком существ (Легион теней). */
    public static final String SUMMON_TAG = "solostatus_summon";
    private static final String FIRST_BOSS_TAG = "solostatus_first_boss";
    /** After the first victory over a boss kind, a guaranteed Epic/Legendary weapon becomes a chance (no farm of legendaries). */
    private static final double REPEAT_BOSS_WEAPON_CHANCE = 0.15;

    private CombatEvents() {}

    /** Призванные существа не переживают перезагрузку мира — нельзя «сохранить» временных союзников. */
    @SubscribeEvent
    public static void onJoinLevel(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getPersistentData().getBoolean(SUMMON_TAG)) event.setCanceled(true);
    }

    // =====================================================================
    // Метка мобов из спавнеров (анти-фарм)
    // =====================================================================

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.SPAWNER) event.getEntity().getPersistentData().putBoolean(Threat.SPAWNER_TAG, true);
        if (type == MobSpawnType.TRIAL_SPAWNER) event.getEntity().getPersistentData().putBoolean(Threat.TRIAL_TAG, true);
    }

    // =====================================================================
    // Входящий урон
    // =====================================================================

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        DamageSource source = event.getSource();

        if (target instanceof ServerPlayer defender) {
            if (handleDefense(event, defender, source)) return;
        }
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != target) {
            handleOffense(event, attacker, target, source);
        }
        // Отбрасывающий выстрел, ледяная стрела, разрывной болт
        Entity direct = source.getDirectEntity();
        if (direct != null && direct.getPersistentData().contains("solostatus_freeze") && source.getEntity() instanceof ServerPlayer shooter) {
            int rank = direct.getPersistentData().getInt("solostatus_freeze");
            direct.getPersistentData().remove("solostatus_freeze");
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60 + 10 * rank, 2), shooter);
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), 200));
            SkillFx.particles((ServerLevel) target.level(), ParticleTypes.SNOWFLAKE, target.getBoundingBox().getCenter(), 25, 0.4, 0.05);
        }
        if (direct != null && direct.getPersistentData().contains("solostatus_explode") && source.getEntity() instanceof ServerPlayer shooter) {
            double dmg = direct.getPersistentData().getDouble("solostatus_explode");
            direct.getPersistentData().remove("solostatus_explode");
            Vec3 at = target.position();
            Scheduler.later(1, () -> {
                if (!shooter.isAlive()) return;
                for (LivingEntity e : SkillFx.enemiesAround(shooter, at, 2.5)) SkillFx.hitPhysical(shooter, e, dmg);
                SkillFx.particles(shooter.serverLevel(), ParticleTypes.EXPLOSION, at.add(0, 0.5, 0), 2, 0.3, 0);
                SkillFx.sound(shooter.serverLevel(), at, SoundEvents.DRAGON_FIREBALL_EXPLODE, 0.7f, 1.3f);
            });
        }
        if (direct != null && direct.getPersistentData().contains("solostatus_knock")) {
            double k = direct.getPersistentData().getDouble("solostatus_knock");
            Vec3 from = target.position().subtract(direct.getDeltaMovement().normalize());
            Scheduler.later(1, () -> SkillFx.knockback(target, from, k));
        }
    }

    /** @return true, если урон полностью отменён. */
    private static boolean handleDefense(LivingIncomingDamageEvent event, ServerPlayer p, DamageSource source) {
        PlayerData d = Hunter.get(p);
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        Entity attackerEntity = source.getEntity();
        boolean byEntity = attackerEntity instanceof LivingEntity && attackerEntity != p;
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);

        if (attackerEntity instanceof LivingEntity le && !(le instanceof Player)) {
            le.getPersistentData().putBoolean(Threat.ENGAGED_TAG, true);
        }

        // Кадры неуязвимости рывков
        if (!bypass && byEntity && d.stateActive("iframe", now)) {
            event.setCanceled(true);
            registerDodge(p, d, (LivingEntity) attackerEntity, now);
            return true;
        }

        // Контратака Защитника
        if (byEntity && source.getDirectEntity() == attackerEntity && d.stateActive("counter", now)) {
            event.setCanceled(true);
            d.timedStates.remove("counter");
            LivingEntity attacker = (LivingEntity) attackerEntity;
            int rank = d.skillRank("counter_attack");
            SkillFx.hitPhysical(p, attacker, SkillFx.melee(p) * (1.5 + 0.3 * (rank - 1)));
            SkillFx.knockback(attacker, p.position(), 0.8);
            SkillFx.particles(level, ParticleTypes.CRIT, attacker.getBoundingBox().getCenter(), 20, 0.3, 0.3);
            SkillFx.sound(level, p.position(), SoundEvents.SHIELD_BLOCK, 1f, 0.6f);
            d.addCounter("counters", 1);
            d.lastBlockTick = now;
            return true;
        }

        // Уклонение (AGI)
        if (!bypass && byEntity && !source.is(DamageTypeTags.IS_EXPLOSION) && p.getRandom().nextDouble() < StatFormulas.dodgeChance(d)) {
            event.setCanceled(true);
            registerDodge(p, d, (LivingEntity) attackerEntity, now);
            return true;
        }

        float amount = event.getAmount();
        double mult = 1.0 - StatFormulas.damageReduction(d);
        if (d.stateActive("defensive_stance", now)) mult *= 1 - (0.30 + 0.04 * (d.skillRank("defensive_stance") - 1));
        if (d.stateActive("unbreakable", now)) mult *= 0.3;
        if (d.stateActive("berserk", now)) mult *= 1.15;
        if (p.getHealth() < p.getMaxHealth() * 0.3f) mult *= 1 - 0.05 * d.skillRank("tenacity");
        mult *= 1 - guardianAura(p);
        if (d.titleIs("dragon_slayer") && attackerEntity instanceof LivingEntity bossAttacker && Threat.isBoss(bossAttacker)) {
            mult *= 0.90; // титул «Драконоборец»
        }
        event.setAmount((float) Math.max(0, amount * mult));

        // Шипы Колосса
        int thorns = d.skillRank("thorns_body");
        if (thorns > 0 && byEntity && source.getDirectEntity() == attackerEntity) {
            LivingEntity attacker = (LivingEntity) attackerEntity;
            float reflect = amount * 0.06f * thorns;
            Scheduler.later(1, () -> {
                if (attacker.isAlive()) attacker.hurt(p.damageSources().thorns(p), reflect);
            });
        }
        // Неотвратимость: Несокрушимый отражает часть урона
        if (d.stateActive("unbreakable", now) && byEntity) {
            LivingEntity attacker = (LivingEntity) attackerEntity;
            float reflect = amount * 0.2f;
            Scheduler.later(1, () -> {
                if (attacker.isAlive()) attacker.hurt(p.damageSources().thorns(p), reflect);
            });
        }
        d.lastDamagedTick = now;
        d.lastCombatTick = now;
        return false;
    }

    /** Защитная аура Стража (Guardian) рядом: -3% урона за ранг пассивки Bulwark. */
    private static double guardianAura(ServerPlayer p) {
        double best = 0;
        for (Player other : p.serverLevel().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(6), o -> o != p)) {
            if (!(other instanceof ServerPlayer sp) || !SkillFx.isAlly(sp, p)) continue;
            int r = Hunter.get(sp).skillRank("bulwark");
            best = Math.max(best, r * 0.03);
        }
        return best;
    }

    private static void registerDodge(ServerPlayer p, PlayerData d, LivingEntity attacker, long now) {
        ServerLevel level = p.serverLevel();
        SkillFx.particles(level, ParticleTypes.CLOUD, p.position().add(0, 1, 0), 8, 0.3, 0.02);
        SkillFx.sound(level, p.position(), SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8f, 1.6f);
        // Счётчик уклонений — не чаще раза в полсекунды и только против настоящих противников
        if (now - d.lastBlockTick > 10 && Threat.fairness(attacker, p) > 0.2) {
            d.addCounter("dodges", 1);
        }
        d.lastBlockTick = now; // уклонение тоже открывает окно контратаки
        int after = d.skillRank("afterimage");
        if (after > 0) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 + after * 4, 1, false, false));
            if (after >= 3) p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false));
        }
    }

    // =====================================================================
    // Исходящий урон игрока
    // =====================================================================

    private static void handleOffense(LivingIncomingDamageEvent event, ServerPlayer p, LivingEntity target, DamageSource source) {
        PlayerData d = Hunter.get(p);
        if (!d.hasClass()) return;
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        d.lastCombatTick = now;
        Entity direct = source.getDirectEntity();
        boolean skill = SkillFx.applyingSkillDamage;
        boolean projectile = direct instanceof Projectile;
        String ironSchool = CompatManager.ironsSchool(source);
        boolean magic = ironSchool != null || source.is(DamageTypeTags.WITCH_RESISTANT_TO);
        boolean melee = !projectile && !magic && direct == p;
        // Отражённый урон (шипы) и прочие косвенные источники не усиливаются характеристиками.
        if (source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)) return;
        if (!melee && !projectile && !magic) return;

        ItemStack weapon = weaponFor(p, direct, projectile);
        WeaponType type = projectile ? rangedType(weapon) : (magic ? WeaponType.STAFF : WeaponClassifier.classify(weapon));
        if (magic && WeaponClassifier.classify(p.getMainHandItem()) != WeaponType.STAFF) type = null;
        WeaponQuality quality = QualitySystem.get(weapon);
        int masteryLevel = type != null ? MasteryMath.level(d.masteryXp(type.key)) : 0;
        HunterClass hc = d.hunterClass;
        Specialization spec = d.specialization;

        double add = 0;
        double critChance = StatFormulas.critChance(d) + (quality != null ? quality.crit() : 0) + MasteryMath.critBonus(masteryLevel);
        boolean forceCrit = false;
        double dist = p.distanceTo(target);

        if (melee) {
            add += StatFormulas.physicalDamage(d);
            add += d.skillRank("warrior_spirit") * 0.04;
            if (hc == HunterClass.WARRIOR) add += 0.05;
        } else if (projectile) {
            add += StatFormulas.projectileDamage(d);
            add += d.skillRank("steady_aim") * 0.05;
            if (hc == HunterClass.ARCHER) add += 0.10;
            if (dist > 20) add += d.skillRank("deadeye") * 0.08;
            if (quality != null) add += quality.effectiveDamageBonus(); // у луков нет атрибута урона
            if (dist > 20 && Threat.fairness(target, p) > 0.2) d.addCounter("long_shots", 1);
        } else if (magic) {
            // С Iron's Spells сила магии идёт через их атрибуты (spell_power), чтобы не масштабировать дважды.
            if (ironSchool == null || !CompatManager.ironsLoaded()) add += StatFormulas.magicPower(d);
            if (hc == HunterClass.MAGE) add += 0.10;
            if (Threat.fairness(target, p) > 0.2) d.addCounter("magic_hits", 1);
        }
        if (type != null) add += MasteryMath.damageBonus(masteryLevel);

        // --- Состояния навыков ---
        if (d.stateActive("berserk", now)) add += 0.20 + 0.05 * (d.skillRank("berserk") - 1);
        int bloodRage = d.skillRank("blood_rage");
        if (bloodRage > 0) add += (1 - p.getHealth() / p.getMaxHealth()) * 0.06 * bloodRage;
        if (melee && d.stateActive("vanish_strike", now)) {
            add += 0.5;
            d.timedStates.remove("vanish_strike");
            p.removeEffect(MobEffects.INVISIBILITY);
        }
        if (melee && d.stateActive("vital_strike", now)) {
            add += 0.5 + 0.15 * (d.skillRank("vital_strike") - 1);
            forceCrit = true;
            d.timedStates.remove("vital_strike");
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0), p);
        }
        if (melee && d.stateActive("poison_blade", now)) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, d.skillRank("poison_blade") >= 4 ? 1 : 0), p);
        }

        // --- Удар в спину ---
        if (melee && isBehind(p, target)) {
            add += 0.10 * d.skillRank("ambush");
            if (hc == HunterClass.THIEF) add += 0.15;
            if (d.titleIs("shadow_of_death")) add += 0.15;
            if (spec == Specialization.SHADOW) forceCrit = true;
            if (!skill && Threat.fairness(target, p) > 0.2) d.addCounter("backstabs", 1);
            target.getPersistentData().putLong("solostatus_backstab", now);
        }

        // --- Метки на цели ---
        if (SkillFx.marked(target, "mark", p)) add += 0.15 + 0.03 * (d.skillRank("hunters_mark") - 1);
        if (SkillFx.marked(target, "sunder", p)) add += 0.15 + 0.03 * (d.skillRank("sunder") - 1);
        if (SkillFx.marked(target, "hex", null)) add += 0.10 + 0.03 * Math.max(0, d.skillRank("hex") - 1);

        // --- Охотник на гигантов и награды достижений ---
        if (d.titleIs("giant_killer") && target.getMaxHealth() > p.getMaxHealth() * 2) add += 0.15;
        if (d.titleIs("mastery_50") && masteryLevel >= MasteryMath.MAX_LEVEL) add += 0.05;

        // --- Награды скрытых достижений ---
        if (d.titleIs("bone_lord") && target.getType().is(EntityTypeTags.UNDEAD)) add += 0.10;
        if (d.titleIs("night_hunter") && level.isNight()) add += 0.08;
        if (d.titleIs("dragon_slayer") && Threat.isBoss(target)) add += 0.10;

        // --- Контратака после блока/уклонения ---
        if (melee && !skill && now - d.lastBlockTick <= 40 && d.lastBlockTick > 0) {
            add += 0.10 * d.skillRank("counter_edge");
            d.lastBlockTick = -1000;
            if (Threat.fairness(target, p) > 0.2) d.addCounter("counters", 1);
        }

        // --- Комбо (мастерство 25+) и ритм клинка ---
        if (melee && !skill && type != null) {
            if (now - d.lastComboTick <= 30 && type.key.equals(d.comboWeapon)) d.comboCount++;
            else d.comboCount = 1;
            d.lastComboTick = now;
            d.comboWeapon = type.key;
            if (masteryLevel >= 25 && d.comboCount % 4 == 0) {
                add += 0.25;
                SkillFx.particles(level, ParticleTypes.ENCHANTED_HIT, target.getBoundingBox().getCenter(), 20, 0.4, 0.2);
                SkillFx.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.5f);
                d.addCounter("combos", 1);
            }
            int rhythm = d.skillRank("blade_rhythm");
            if (rhythm > 0) add += Math.min(5, d.comboCount) * (0.03 + 0.01 * (rhythm - 1));
        }

        float amount = (float) (event.getAmount() * Math.max(0.1, 1 + add));

        // --- Крит системы (отдельно от ванильного крита в прыжке) ---
        if (forceCrit || p.getRandom().nextDouble() < Math.min(0.6, critChance)) {
            amount *= (float) (1 + StatFormulas.critDamage(d));
            SkillFx.particles(level, ParticleTypes.CRIT, target.getBoundingBox().getCenter(), 12, 0.3, 0.3);
            if (Threat.fairness(target, p) > 0.2) d.addCounter("crits", 1);
        }
        event.setAmount(amount);

        // --- Пассивки оружия второго круга ---
        if (melee && !skill && type == WeaponType.GREATSWORD && p.getRandom().nextDouble() < 0.05 * d.skillRank("titan_grip")) {
            SkillFx.stun(p, target, 20);
            SkillFx.particles(level, ParticleTypes.CRIT, target.getBoundingBox().getCenter(), 10, 0.3, 0.2);
        }
        // --- Клинок маны: дополнительный магический урон ---
        if (melee && !skill && d.manaBladeCharges > 0 && d.stateActive("mana_blade", now)) {
            d.manaBladeCharges--;
            double extra = StatFormulas.baseSpellDamage(d) * (0.3 + 0.08 * (d.skillRank("mana_blade") - 1));
            Scheduler.later(1, () -> {
                if (target.isAlive()) SkillFx.hitMagic(p, target, extra);
            });
            SkillFx.particles(level, SkillFx.SYSTEM_DUST, target.getBoundingBox().getCenter(), 10, 0.3, 0.02);
        }

        // --- Вампиризм Берсерка ---
        if (d.stateActive("berserk", now)) p.heal(amount * 0.10f);
        // --- Резонанс Чародея: шанс ослабить ---
        int resonance = d.skillRank("resonance");
        if (resonance > 0 && p.getRandom().nextDouble() < 0.03 * resonance) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), p);
        }
        // --- Ловкость следопыта ---
        if (projectile && d.skillRank("fleet_foot") > 0) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false));

        // --- Прогресс мастерства за попадание ---
        double fairness = Threat.fairness(target, p);
        if (fairness > 0 && amount >= 1) {
            var tag = target.getPersistentData();
            int hits = tag.getInt(Threat.HITS_TAG);
            if (hits < 12) { // не больше 12 засчитанных попаданий по одной цели
                tag.putInt(Threat.HITS_TAG, hits + 1);
                double gain = 1.0 * Math.sqrt(Math.min(4, Threat.of(target))) * fairness * (skill ? 0.5 : 1.0);
                if (type != null && !magic) MasterySystem.addWeapon(p, d, type, gain);
                if (magic) {
                    MasterySystem.addMagic(p, d, ironSchool != null ? ironSchool : "arcane", gain);
                    if (type == WeaponType.STAFF) MasterySystem.addWeapon(p, d, WeaponType.STAFF, gain * 0.5);
                }
            }
        }
        target.getPersistentData().putBoolean(Threat.ENGAGED_TAG, target.getPersistentData().getBoolean(Threat.ENGAGED_TAG)
                || (target instanceof net.minecraft.world.entity.Mob m && m.getTarget() == p));
    }

    /** Опыт за босса (первая победа над этим видом; повторные — 25%). */
    private static double bossXp(String type, LivingEntity dead) {
        return switch (type) {
            case "minecraft:ender_dragon" -> 15000;
            case "minecraft:wither" -> 10000;
            case "minecraft:warden" -> 8000;
            case "minecraft:elder_guardian" -> 3000;
            default -> 2000 + Math.min(20000, dead.getMaxHealth() * 10);
        };
    }

    private static net.minecraft.world.item.Item bossIcon(String type) {
        return switch (type) {
            case "minecraft:ender_dragon" -> net.minecraft.world.item.Items.DRAGON_HEAD;
            case "minecraft:wither" -> net.minecraft.world.item.Items.NETHER_STAR;
            case "minecraft:warden" -> net.minecraft.world.item.Items.SCULK_CATALYST;
            case "minecraft:elder_guardian" -> net.minecraft.world.item.Items.PRISMARINE_SHARD;
            default -> net.minecraft.world.item.Items.WITHER_SKELETON_SKULL;
        };
    }


    private static boolean isBehind(Player attacker, LivingEntity target) {
        Vec3 look = target.getViewVector(1.0f).multiply(1, 0, 1);
        Vec3 toAttacker = attacker.position().subtract(target.position()).multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0e-4 || toAttacker.lengthSqr() < 1.0e-4) return false;
        return look.normalize().dot(toAttacker.normalize()) < -0.3;
    }

    private static ItemStack weaponFor(ServerPlayer p, Entity direct, boolean projectile) {
        if (projectile && direct instanceof AbstractArrow arrow) {
            ItemStack w = arrow.getWeaponItem();
            if (w != null && !w.isEmpty()) return w;
        }
        if (projectile) {
            ItemStack main = p.getMainHandItem();
            if (WeaponClassifier.classify(main).ranged()) return main;
            ItemStack off = p.getOffhandItem();
            if (WeaponClassifier.classify(off).ranged()) return off;
        }
        return p.getMainHandItem();
    }

    private static WeaponType rangedType(ItemStack weapon) {
        WeaponType t = WeaponClassifier.classify(weapon);
        return t.ranged() ? t : (t == WeaponType.SPEAR ? WeaponType.SPEAR : null);
    }

    // =====================================================================
    // Ванильный крит в прыжке
    // =====================================================================

    @SubscribeEvent
    public static void onCrit(CriticalHitEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.isCriticalHit() && event.getTarget() instanceof LivingEntity t) {
            if (Threat.fairness(t, p) > 0.2) Hunter.get(p).addCounter("crits", 1);
        }
    }

    // =====================================================================
    // Блок щитом и идеальная защита
    // =====================================================================

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer p && WeaponClassifier.classify(event.getItem()) == WeaponType.SHIELD) {
            Hunter.get(p).shieldRaisedTick = p.serverLevel().getGameTime();
        }
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !event.getBlocked()) return;
        PlayerData d = Hunter.get(p);
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        Entity attackerEntity = event.getDamageSource().getEntity();
        LivingEntity attacker = attackerEntity instanceof LivingEntity le ? le : null;
        double fairness = attacker != null ? Threat.fairness(attacker, p) : 0;
        if (attacker != null && !(attacker instanceof Player)) attacker.getPersistentData().putBoolean(Threat.ENGAGED_TAG, true);

        boolean counted = now - d.lastBlockTick > 10 && fairness > 0.2;
        d.lastBlockTick = now;
        if (counted) {
            d.addCounter("blocks", 1);
            MasterySystem.addWeapon(p, d, WeaponType.SHIELD, 2.5 * Math.sqrt(Math.min(4, Threat.of(attacker))) * fairness);
        }

        // Идеальная защита: блок в первые 10 тиков (+1 за ранг) после поднятия щита
        int perfect = d.skillRank("perfect_guard");
        long window = 10 + perfect;
        if (now - d.shieldRaisedTick <= window) {
            if (counted) d.addCounter("perfect_guards", 1);
            SkillFx.particles(level, ParticleTypes.ENCHANTED_HIT, p.position().add(p.getLookAngle()).add(0, 1.2, 0), 15, 0.2, 0.2);
            SkillFx.sound(level, p.position(), SoundEvents.ANVIL_PLACE, 0.5f, 1.8f);
            if (perfect > 0 && attacker != null && event.getDamageSource().getDirectEntity() == attacker) {
                float reflect = event.getBlockedDamage() * (0.3f + 0.1f * perfect);
                SkillFx.stun(p, attacker, 20);
                Scheduler.later(1, () -> {
                    if (attacker.isAlive()) SkillFx.hitPhysical(p, attacker, reflect);
                });
            }
        }
        // Бастион Стража: поглощение при блоке
        if (d.skillRank("bulwark") > 0 && !d.stateActive("bulwark_cd", now)) {
            p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60, 0, false, true));
            SkillFx.state(d, level, "bulwark_cd", 100);
        }
    }

    // =====================================================================
    // Смерть: спасительные навыки и убийства
    // =====================================================================

    /** Saving skills run early, so other mods see the player alive. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) return;
        if (dead instanceof ServerPlayer p && trySave(p)) event.setCanceled(true);
    }

    /**
     * The kill is rewarded last: if any mod cancels the death (a revive, a totem-like item, a boss phase),
     * this handler does not run, so a target that did not die gives no XP or mastery.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKillConfirmed(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) return;
        if (event.getSource().getEntity() instanceof ServerPlayer killer && killer != dead) {
            onKill(killer, dead, event.getSource());
        }
    }

    private static boolean trySave(ServerPlayer p) {
        PlayerData d = Hunter.get(p);
        long now = p.serverLevel().getGameTime();
        if (d.stateActive("guardian_angel", now)) {
            d.timedStates.remove("guardian_angel");
            p.setHealth(Math.max(1, p.getMaxHealth() * 0.3f));
            SkillFx.particles(p.serverLevel(), ParticleTypes.TOTEM_OF_UNDYING, p.position().add(0, 1, 0), 40, 0.5, 0.4);
            SkillFx.sound(p.serverLevel(), p.position(), SoundEvents.TOTEM_USE, 0.8f, 1.3f);
            return true;
        }
        int wind = d.skillRank("second_wind");
        if (wind > 0 && !d.stateActive("second_wind_cd", now)) {
            SkillFx.state(d, p.serverLevel(), "second_wind_cd", 20 * 60 * (6 - wind)); // 5/4/3 минуты
            p.setHealth(Math.max(1, p.getMaxHealth() * (0.15f + 0.05f * wind)));
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 2));
            SkillFx.particles(p.serverLevel(), SkillFx.SYSTEM_DUST, p.position().add(0, 1, 0), 60, 0.6, 0.05);
            SkillFx.sound(p.serverLevel(), p.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.8f);
            return true;
        }
        return false;
    }

    private static void onKill(ServerPlayer p, LivingEntity dead, DamageSource source) {
        PlayerData d = Hunter.get(p);
        if (!d.hasClass()) return;
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        String type = Threat.typeId(dead);
        double threat = Threat.of(dead);
        double fairness = Threat.fairness(dead, p) * Threat.diversity(d, type);
        boolean boss = Threat.isBoss(dead);
        boolean counts = fairness > 0.2;

        // --- Опыт системы ---
        // Разница «уровня» врага и охотника: слабые для вашего уровня враги дают меньше (до 30%),
        // сильные — больше (до 150%). Ожидаемая опасность растёт с уровнем: 1 + уровень × 0.06.
        double expected = 1 + d.level * 0.06;
        double gap = Math.max(0.3, Math.min(1.5, threat / (expected * 0.6)));
        if (boss && fairness > 0) { // a boss without AI (statue, command dummy) gives nothing
            boolean first = !d.bossesKilled.contains(type);
            if (first) dead.getPersistentData().putBoolean(FIRST_BOSS_TAG, true); // read by onDrops
            double bossXp = bossXp(type, dead) * (first ? 1.0 : 0.25);
            LevelSystem.grantXp(p, bossXp * Math.max(0.5, fairness), com.vladosiwe.solostatus.level.XpReason.BOSS);
            com.vladosiwe.solostatus.network.Notices.send(p, com.vladosiwe.solostatus.network.Notices.ACHIEVEMENT,
                    net.minecraft.network.chat.Component.translatable(first ? "solostatus.notice.boss_first" : "solostatus.notice.boss", dead.getDisplayName()),
                    net.minecraft.network.chat.Component.translatable("solostatus.notice.boss.desc",
                            Math.round(bossXp * Math.max(0.5, fairness) * com.vladosiwe.solostatus.config.SoloConfig.XP_MULTIPLIER.get())),
                    bossIcon(type));
        } else if (!boss) {
            LevelSystem.grantXp(p, 10 * Math.pow(threat, 1.1) * gap * fairness, com.vladosiwe.solostatus.level.XpReason.KILL);
        }

        if (fairness <= 0) return;

        d.recentKills.addLast(type);
        while (d.recentKills.size() > PlayerData.RECENT_KILLS_WINDOW) d.recentKills.pollFirst();
        if (!counts) return;

        d.addCounter("kills", 1);
        // Новый вид противника — бонус за исследование (разнообразие боёв)
        if (!d.killsByType.containsKey(type) && !boss) {
            LevelSystem.grantXp(p, 50 + 25 * Math.min(10, threat), com.vladosiwe.solostatus.level.XpReason.DISCOVERY);
        }
        d.killsByType.merge(type, 1, Integer::sum);
        Entity direct = source.getDirectEntity();
        boolean projectile = direct instanceof Projectile;
        boolean thorns = source.is(net.minecraft.world.damagesource.DamageTypes.THORNS);
        boolean magic = !thorns && (CompatManager.ironsSchool(source) != null || source.is(DamageTypeTags.WITCH_RESISTANT_TO));
        // the same rule as for hits: melee is a blow by the player, not thorns, TNT or other indirect damage
        boolean melee = !projectile && !magic && !thorns && direct == p;
        if (projectile) d.addCounter("projectile_kills", 1);
        else if (magic) d.addCounter("magic_kills", 1);
        else if (melee) d.addCounter("melee_kills", 1);
        if (threat >= Threat.STRONG || boss) d.addCounter("strong_kills", 1);
        if (dead.getType().is(EntityTypeTags.SKELETONS)) d.addCounter("skeleton_kills", 1);
        if (dead.getType() == EntityType.SPIDER || dead.getType() == EntityType.CAVE_SPIDER) d.addCounter("spider_kills", 1);
        if (level.isNight()) d.addCounter("night_kills", 1);
        if (p.getHealth() <= p.getMaxHealth() * 0.3f) d.addCounter("low_hp_kills", 1);
        if (p.getHealth() <= 2.0f) d.addCounter("undying", 1);
        if (now - dead.getPersistentData().getLong("solostatus_backstab") < 40) d.addCounter("backstab_kills", 1);
        if ((threat >= Threat.STRONG || boss) && d.lastDamagedTick < now - 600) d.addCounter("flawless_strong", 1);
        if (boss) {
            d.addCounter("boss_kills", 1);
            // Одиночный рейд: рядом нет других игроков
            boolean alone = level.getEntitiesOfClass(Player.class, dead.getBoundingBox().inflate(64), o -> o != p && !o.isSpectator()).isEmpty();
            if (alone) d.addCounter("solo_boss", 1);
            d.bossesKilled.add(type);
        }

        // --- Мастерство за убийство ---
        double gain = 12 * Math.pow(threat, 0.8) * fairness * (boss ? 3 : 1);
        if (projectile) {
            WeaponType t = rangedType(weaponFor(p, direct, true));
            if (t != null) MasterySystem.addWeapon(p, d, t, gain);
        } else if (magic) {
            String school = CompatManager.ironsSchool(source);
            MasterySystem.addMagic(p, d, school != null ? school : "arcane", gain);
        } else if (melee) {
            MasterySystem.addWeapon(p, d, WeaponClassifier.classify(p.getMainHandItem()), gain);
        }

        // --- Неистовство: 3 победы за 10 секунд ---
        d.killTimes.addLast(now);
        while (!d.killTimes.isEmpty() && now - d.killTimes.peekFirst() > 200) d.killTimes.pollFirst();
        if (d.killTimes.size() >= 3 && !d.stateActive("rampage_cd", now)) {
            SkillFx.state(d, level, "rampage_cd", 200);
            d.addCounter("rampages", 1);
            int rampage = d.skillRank("rampage");
            if (rampage > 0) {
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100 + 20 * rampage, rampage >= 4 ? 1 : 0), p);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100 + 20 * rampage, 0), p);
                SkillFx.particles(level, SkillFx.BLOOD_DUST, p.position().add(0, 1, 0), 30, 0.5, 0.05);
            }
        }
        if (threat >= 8 && !boss) d.addCounter("giant_kills", 1);

        // --- Пассивки на убийство ---
        int commander = d.skillRank("commander");
        if (commander > 0) {
            for (LivingEntity a : SkillFx.alliesAround(p, p.position(), 8)) {
                a.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40 + commander * 20, 0), p);
            }
        }
        int veil = d.skillRank("shadow_veil");
        if (veil > 0) {
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * (1 + veil), 0, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * (1 + veil), 0, false, false));
        }
        d.markDirty();
    }

    // =====================================================================
    // Добыча: удача, качество оружия, боссы
    // =====================================================================

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p)) return;
        LivingEntity dead = event.getEntity();
        PlayerData d = Hunter.get(p);
        if (!d.hasClass()) return;
        double luck = StatFormulas.qualityBonus(d);

        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (WeaponClassifier.isQualityWeapon(stack) && QualitySystem.get(stack) == null) {
                QualitySystem.roll(stack, Threat.isBoss(dead) ? QualitySystem.Source.BOSS : QualitySystem.Source.MOB, p.getRandom(), luck);
            }
        }

        if (Threat.fairness(dead, p) <= 0.2) return;

        // Удача: шанс удвоить один не-оружейный предмет добычи
        if (p.getRandom().nextDouble() < StatFormulas.extraDropChance(d)) {
            List<ItemEntity> candidates = new ArrayList<>();
            for (ItemEntity e : event.getDrops()) if (!WeaponClassifier.isQualityWeapon(e.getItem())) candidates.add(e);
            if (!candidates.isEmpty()) {
                ItemEntity src = candidates.get(p.getRandom().nextInt(candidates.size()));
                ItemStack copy = src.getItem().copy();
                event.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(), copy));
                SkillFx.particles(p.serverLevel(), ParticleTypes.HAPPY_VILLAGER, dead.position().add(0, 0.5, 0), 8, 0.3, 0.05);
            }
        }

        // Боссы: гарантированное оружие Epic/Legendary и шанс Сферы перерождения
        if (Threat.isBoss(dead)) {
            if (dead.getPersistentData().getBoolean(FIRST_BOSS_TAG) || p.getRandom().nextDouble() < REPEAT_BOSS_WEAPON_CHANCE) {
                ItemStack weapon = QualitySystem.bossWeapon(p.getRandom(), luck);
                event.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(), weapon));
            }
            if (p.getRandom().nextDouble() < com.vladosiwe.solostatus.config.SoloConfig.BOSS_ORB_CHANCE.get()) {
                event.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(),
                        new ItemStack(com.vladosiwe.solostatus.registry.ModItems.RESET_ORB.get())));
            }
        }
    }

    // =====================================================================
    // Сопротивление эффектам (VIT и награды достижений)
    // =====================================================================

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        MobEffectInstance inst = event.getEffectInstance();
        if (inst == null || inst.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return;
        PlayerData d = Hunter.get(p);
        var effect = inst.getEffect();
        var e = effect.value();
        boolean immune = (e == MobEffects.WITHER.value() && d.titleIs("witherbane"))
                || ((e == MobEffects.DARKNESS.value() || e == MobEffects.BLINDNESS.value()) && d.titleIs("deep_dark"));
        if (immune || p.getRandom().nextDouble() < StatFormulas.debuffResist(d)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
}
