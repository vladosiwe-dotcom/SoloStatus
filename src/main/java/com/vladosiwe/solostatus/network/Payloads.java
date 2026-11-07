package com.vladosiwe.solostatus.network;

import com.vladosiwe.solostatus.SoloStatus;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Все сетевые пакеты мода. Клиент отправляет только запросы (C2S) — сервер их проверяет.
 * Сервер отправляет только данные, разрешённые конкретному игроку (S2C).
 */
public final class Payloads {
    private Payloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(SoloStatus.id(name));
    }

    // ======================= S2C =======================

    /** Полный собственный профиль игрока (только самому игроку). */
    public record SyncData(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<SyncData> TYPE = Payloads.typeOf("sync_data");
        public static final StreamCodec<ByteBuf, SyncData> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, SyncData::tag, SyncData::new);
        @Override public Type<SyncData> type() { return TYPE; }
    }

    /** Лёгкая синхронизация энергии (раз в секунду при изменении). */
    public record EnergySync(float energy) implements CustomPacketPayload {
        public static final Type<EnergySync> TYPE = Payloads.typeOf("energy");
        public static final StreamCodec<ByteBuf, EnergySync> CODEC =
                StreamCodec.composite(ByteBufCodecs.FLOAT, EnergySync::energy, EnergySync::new);
        @Override public Type<EnergySync> type() { return TYPE; }
    }

    /** Системное уведомление (повышение уровня, пробуждение навыка, достижение...). */
    public record Notice(int kind, Component title, Component body) implements CustomPacketPayload {
        public static final Type<Notice> TYPE = Payloads.typeOf("notice");
        public static final StreamCodec<RegistryFriendlyByteBuf, Notice> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Notice::kind,
                ComponentSerialization.STREAM_CODEC, Notice::title,
                ComponentSerialization.STREAM_CODEC, Notice::body,
                Notice::new);
        @Override public Type<Notice> type() { return TYPE; }
    }

    /** Результат осмотра другого игрока: только разрешённые поля. */
    public record InspectResult(int entityId, CompoundTag tag) implements CustomPacketPayload {
        public static final Type<InspectResult> TYPE = Payloads.typeOf("inspect_result");
        public static final StreamCodec<ByteBuf, InspectResult> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, InspectResult::entityId,
                ByteBufCodecs.COMPOUND_TAG, InspectResult::tag,
                InspectResult::new);
        @Override public Type<InspectResult> type() { return TYPE; }
    }

    // ======================= C2S =======================

    public record AllocateStat(int stat, int amount) implements CustomPacketPayload {
        public static final Type<AllocateStat> TYPE = Payloads.typeOf("allocate_stat");
        public static final StreamCodec<ByteBuf, AllocateStat> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AllocateStat::stat,
                ByteBufCodecs.VAR_INT, AllocateStat::amount,
                AllocateStat::new);
        @Override public Type<AllocateStat> type() { return TYPE; }
    }

    public record ChooseClass(int index) implements CustomPacketPayload {
        public static final Type<ChooseClass> TYPE = Payloads.typeOf("choose_class");
        public static final StreamCodec<ByteBuf, ChooseClass> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, ChooseClass::index, ChooseClass::new);
        @Override public Type<ChooseClass> type() { return TYPE; }
    }

    public record ChooseSpec(int index) implements CustomPacketPayload {
        public static final Type<ChooseSpec> TYPE = Payloads.typeOf("choose_spec");
        public static final StreamCodec<ByteBuf, ChooseSpec> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, ChooseSpec::index, ChooseSpec::new);
        @Override public Type<ChooseSpec> type() { return TYPE; }
    }

    /** Изучить или улучшить навык. */
    public record LearnSkill(String id) implements CustomPacketPayload {
        public static final Type<LearnSkill> TYPE = Payloads.typeOf("learn_skill");
        public static final StreamCodec<ByteBuf, LearnSkill> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, LearnSkill::id, LearnSkill::new);
        @Override public Type<LearnSkill> type() { return TYPE; }
    }

    /** Назначить активный навык в слот (пустая строка — очистить). */
    public record EquipSkill(int slot, String id) implements CustomPacketPayload {
        public static final Type<EquipSkill> TYPE = Payloads.typeOf("equip_skill");
        public static final StreamCodec<ByteBuf, EquipSkill> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EquipSkill::slot,
                ByteBufCodecs.STRING_UTF8, EquipSkill::id,
                EquipSkill::new);
        @Override public Type<EquipSkill> type() { return TYPE; }
    }

    public record CastSkill(int slot) implements CustomPacketPayload {
        public static final Type<CastSkill> TYPE = Payloads.typeOf("cast_skill");
        public static final StreamCodec<ByteBuf, CastSkill> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, CastSkill::slot, CastSkill::new);
        @Override public Type<CastSkill> type() { return TYPE; }
    }

    /** Сброс: 0 — навыки, 1 — характеристики. Требует Сферу перерождения. */
    public record Respec(int mode) implements CustomPacketPayload {
        public static final Type<Respec> TYPE = Payloads.typeOf("respec");
        public static final StreamCodec<ByteBuf, Respec> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Respec::mode, Respec::new);
        @Override public Type<Respec> type() { return TYPE; }
    }

    public record InspectRequest(int entityId) implements CustomPacketPayload {
        public static final Type<InspectRequest> TYPE = Payloads.typeOf("inspect_request");
        public static final StreamCodec<ByteBuf, InspectRequest> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, InspectRequest::entityId, InspectRequest::new);
        @Override public Type<InspectRequest> type() { return TYPE; }
    }

    /** Отметить просмотренным: 0 — достижения, 1 — навыки. */
    public record MarkSeen(int what) implements CustomPacketPayload {
        public static final Type<MarkSeen> TYPE = Payloads.typeOf("mark_seen");
        public static final StreamCodec<ByteBuf, MarkSeen> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, MarkSeen::what, MarkSeen::new);
        @Override public Type<MarkSeen> type() { return TYPE; }
    }

    public record SetPrivacy(boolean hidden) implements CustomPacketPayload {
        public static final Type<SetPrivacy> TYPE = Payloads.typeOf("set_privacy");
        public static final StreamCodec<ByteBuf, SetPrivacy> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, SetPrivacy::hidden, SetPrivacy::new);
        @Override public Type<SetPrivacy> type() { return TYPE; }
    }
}
