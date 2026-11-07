package com.vladosiwe.solostatus.client;

import com.vladosiwe.solostatus.client.screen.InspectScreen;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Обработка пакетов сервера на клиенте. Загружается только на физическом клиенте. */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}

    public static void sync(Payloads.SyncData msg) {
        PlayerData d = new PlayerData();
        d.read(msg.tag());
        ClientState.data = d;
        ClientState.synced = true;
    }

    public static void energy(Payloads.EnergySync msg) {
        ClientState.data.energy = msg.energy();
    }

    public static void notice(Payloads.Notice msg) {
        ClientState.addNotice(msg.kind(), msg.title(), msg.body(), msg.icon());
        if (msg.kind() == 6) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.6f));
        }
    }

    public static void xpGain(Payloads.XpGain msg) {
        ClientState.addXpPopup(msg.reason(), msg.amount());
    }

    public static void inspect(Payloads.InspectResult msg) {
        ClientState.inspectData = msg.tag();
        ClientState.inspectEntityId = msg.entityId();
        Minecraft.getInstance().setScreen(new InspectScreen(msg.entityId(), msg.tag()));
    }
}
