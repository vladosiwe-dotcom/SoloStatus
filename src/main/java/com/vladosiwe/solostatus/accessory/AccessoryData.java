package com.vladosiwe.solostatus.accessory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.vladosiwe.solostatus.quality.QualityRarity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** One piece of jewellery (data component solostatus:accessory): its rarity and rolled bonuses. */
public record AccessoryData(int rarity, List<Roll> rolls) {
    public record Roll(String affix, float value) {
        public static final Codec<Roll> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("affix").forGetter(Roll::affix),
                Codec.FLOAT.fieldOf("value").forGetter(Roll::value)
        ).apply(i, Roll::new));
        public static final StreamCodec<ByteBuf, Roll> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Roll::affix, ByteBufCodecs.FLOAT, Roll::value, Roll::new);
    }

    public static final Codec<AccessoryData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("rarity").forGetter(AccessoryData::rarity),
            Roll.CODEC.listOf().fieldOf("rolls").forGetter(AccessoryData::rolls)
    ).apply(i, AccessoryData::new));

    public static final StreamCodec<ByteBuf, AccessoryData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AccessoryData::rarity,
            Roll.STREAM_CODEC.apply(ByteBufCodecs.list()), AccessoryData::rolls,
            AccessoryData::new);

    public AccessoryData {
        rolls = List.copyOf(rolls);
    }

    public QualityRarity tier() {
        return QualityRarity.byIndex(rarity);
    }
}
