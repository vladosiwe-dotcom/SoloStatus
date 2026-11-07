package com.vladosiwe.solostatus.quality;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Индивидуальные параметры экземпляра оружия (data component solostatus:quality).
 * Намеренно небольшое число параметров — их можно сбалансировать.
 *
 * @param rarity    индекс {@link QualityRarity}
 * @param damage    бонус урона (доля, например 0.12 = +12%)
 * @param speed     бонус скорости атаки (доля)
 * @param crit      бонус шанса крита (доля)
 * @param condition 0 — целое, 1 — повреждённое (снижена эффективность)
 * @param broken    сломано: сильный штраф, ремонт невозможен
 */
public record WeaponQuality(int rarity, float damage, float speed, float crit, int condition, boolean broken) {
    public static final int PRISTINE = 0;
    public static final int DAMAGED = 1;

    public static final Codec<WeaponQuality> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("rarity").forGetter(WeaponQuality::rarity),
            Codec.FLOAT.fieldOf("damage").forGetter(WeaponQuality::damage),
            Codec.FLOAT.optionalFieldOf("speed", 0f).forGetter(WeaponQuality::speed),
            Codec.FLOAT.optionalFieldOf("crit", 0f).forGetter(WeaponQuality::crit),
            Codec.INT.optionalFieldOf("condition", 0).forGetter(WeaponQuality::condition),
            Codec.BOOL.optionalFieldOf("broken", false).forGetter(WeaponQuality::broken)
    ).apply(i, WeaponQuality::new));

    public static final StreamCodec<ByteBuf, WeaponQuality> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeaponQuality::rarity,
            ByteBufCodecs.FLOAT, WeaponQuality::damage,
            ByteBufCodecs.FLOAT, WeaponQuality::speed,
            ByteBufCodecs.FLOAT, WeaponQuality::crit,
            ByteBufCodecs.VAR_INT, WeaponQuality::condition,
            ByteBufCodecs.BOOL, WeaponQuality::broken,
            WeaponQuality::new);

    public QualityRarity tier() {
        return QualityRarity.byIndex(rarity);
    }

    /** Итоговый множитель урона с учётом состояния. */
    public float effectiveDamageBonus() {
        float d = damage;
        if (condition == DAMAGED) d -= 0.20f;
        if (broken) d -= 0.50f;
        return d;
    }

    public WeaponQuality withBroken() {
        return new WeaponQuality(rarity, damage, speed, crit, condition, true);
    }
}
