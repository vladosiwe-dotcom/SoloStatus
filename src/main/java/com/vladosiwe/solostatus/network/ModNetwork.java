package com.vladosiwe.solostatus.network;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.client.ClientPayloadHandler;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Регистрация пакетов NeoForge Networking.
 * Клиентские обработчики вызываются только на физическом клиенте: тело лямбды разрешает
 * класс ClientPayloadHandler лишь при выполнении, поэтому на выделенном сервере он не загружается.
 */
@EventBusSubscriber(modid = SoloStatus.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {
    public static final String VERSION = "1";

    private ModNetwork() {}

    /**
     * Отправка пакета игроку. Пропускает FakePlayer (их создают другие моды, например механизмы Create)
     * и соединения без нашего канала: NeoForge бросает исключение при попытке отправить им пакет.
     */
    public static void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player instanceof FakePlayer || player.connection == null || !player.connection.hasChannel(payload)) return;
        PacketDistributor.sendToPlayer(player, payload);
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION);

        r.playToClient(Payloads.SyncData.TYPE, Payloads.SyncData.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.sync(p)));
        r.playToClient(Payloads.EnergySync.TYPE, Payloads.EnergySync.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.energy(p)));
        r.playToClient(Payloads.Notice.TYPE, Payloads.Notice.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.notice(p)));
        r.playToClient(Payloads.XpGain.TYPE, Payloads.XpGain.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.xpGain(p)));
        r.playToClient(Payloads.Presence.TYPE, Payloads.Presence.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.presence(p)));
        r.playToClient(Payloads.AccessorySync.TYPE, Payloads.AccessorySync.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.accessories(p)));
        r.playToClient(Payloads.Awakening.TYPE, Payloads.Awakening.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.vladosiwe.solostatus.client.awakening.AwakeningClient.onPacket(p)));
        r.playToClient(Payloads.InspectResult.TYPE, Payloads.InspectResult.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.inspect(p)));

        r.playToServer(Payloads.UnequipAccessory.TYPE, Payloads.UnequipAccessory.CODEC, ServerPayloadHandler::unequipAccessory);
        r.playToServer(Payloads.AllocateStat.TYPE, Payloads.AllocateStat.CODEC, ServerPayloadHandler::allocateStat);
        r.playToServer(Payloads.ChooseClass.TYPE, Payloads.ChooseClass.CODEC, ServerPayloadHandler::chooseClass);
        r.playToServer(Payloads.ChooseSpec.TYPE, Payloads.ChooseSpec.CODEC, ServerPayloadHandler::chooseSpec);
        r.playToServer(Payloads.LearnSkill.TYPE, Payloads.LearnSkill.CODEC, ServerPayloadHandler::learnSkill);
        r.playToServer(Payloads.EquipSkill.TYPE, Payloads.EquipSkill.CODEC, ServerPayloadHandler::equipSkill);
        r.playToServer(Payloads.CastSkill.TYPE, Payloads.CastSkill.CODEC, ServerPayloadHandler::castSkill);
        r.playToServer(Payloads.Respec.TYPE, Payloads.Respec.CODEC, ServerPayloadHandler::respec);
        r.playToServer(Payloads.InspectRequest.TYPE, Payloads.InspectRequest.CODEC, ServerPayloadHandler::inspect);
        r.playToServer(Payloads.MarkSeen.TYPE, Payloads.MarkSeen.CODEC, ServerPayloadHandler::markSeen);
        r.playToServer(Payloads.SystemOpen.TYPE, Payloads.SystemOpen.CODEC, ServerPayloadHandler::systemOpen);
        r.playToServer(Payloads.SetTitle.TYPE, Payloads.SetTitle.CODEC, ServerPayloadHandler::setTitle);
        r.playToServer(Payloads.SetPrivacy.TYPE, Payloads.SetPrivacy.CODEC, ServerPayloadHandler::setPrivacy);
    }
}
