package com.vladosiwe.solostatus.combat;

import com.vladosiwe.solostatus.config.SoloConfig;
import com.vladosiwe.solostatus.data.PlayerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.Tags;

/**
 * Оценка опасности противника и защита от фарма.
 *
 * Прогресс (опыт, мастерство, счётчики пробуждения) умножается на фактор «честности боя»:
 *  - существа без ИИ (манекены, статуи) — 0;
 *  - мобы из обычных спавнеров — spawnerFactor (0.25), из испытательных спавнеров — 0.8;
 *  - однообразие: если тип составляет большую долю последних 30 убийств — до 0.3;
 *  - моб ни разу не вступал в бой с игроком (ловушки, безопасный фарм) — 0.6.
 */
public final class Threat {
    public static final String SPAWNER_TAG = "solostatus_spawner";
    public static final String TRIAL_TAG = "solostatus_trial";
    public static final String ENGAGED_TAG = "solostatus_engaged";
    public static final String HITS_TAG = "solostatus_hits";
    public static final double STRONG = 3.0;

    private Threat() {}

    public static double of(LivingEntity e) {
        double hp = Math.max(1, e.getMaxHealth());
        double atk = e.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? e.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
        double armor = e.getAttributes().hasAttribute(Attributes.ARMOR) ? e.getAttributeValue(Attributes.ARMOR) : 0;
        return Math.pow(hp / 20.0, 0.8) * (1 + atk / 8.0) * (1 + armor / 30.0);
    }

    public static boolean isBoss(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES) || (e.getMaxHealth() >= 150 && !(e instanceof Player));
    }

    public static String typeId(LivingEntity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    }

    /** Фактор честности боя против цели (без учёта разнообразия). */
    public static double fairness(LivingEntity target, Player player) {
        if (target instanceof Player) return SoloConfig.PVP_PROGRESS_FACTOR.get();
        if (!(target instanceof Mob mob)) return 0; // стойки, манекены и прочие не-мобы
        if (mob.isNoAi()) return 0;
        double f = 1.0;
        var tag = target.getPersistentData();
        if (tag.getBoolean(SPAWNER_TAG)) f *= SoloConfig.SPAWNER_FACTOR.get();
        if (tag.getBoolean(TRIAL_TAG)) f *= 0.8;
        boolean engaged = tag.getBoolean(ENGAGED_TAG) || mob.getTarget() == player;
        if (!engaged) f *= 0.6;
        return f;
    }

    /** Доля текущего типа среди последних убийств → множитель разнообразия. */
    public static double diversity(PlayerData d, String type) {
        if (d.recentKills.size() < 10) return 1.0;
        long same = d.recentKills.stream().filter(type::equals).count();
        double share = same / (double) d.recentKills.size();
        return Math.max(0.3, Math.min(1.0, 1.3 - share));
    }
}
