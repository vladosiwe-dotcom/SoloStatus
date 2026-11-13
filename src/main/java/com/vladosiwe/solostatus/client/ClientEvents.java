package com.vladosiwe.solostatus.client;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.client.ui.SystemScreen;
import com.vladosiwe.solostatus.client.voice.VoiceClient;
import com.vladosiwe.solostatus.combat.WeaponClassifier;
import com.vladosiwe.solostatus.combat.WeaponType;
import com.vladosiwe.solostatus.config.SoloConfig;
import com.vladosiwe.solostatus.network.Payloads;
import com.vladosiwe.solostatus.quality.QualitySystem;
import com.vladosiwe.solostatus.quality.WeaponQuality;
import com.vladosiwe.solostatus.voice.VoiceCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Клиентские игровые события: клавиши, фраза в чате, подсказки качества оружия. */
@EventBusSubscriber(modid = SoloStatus.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ClientState.pumpNotices(System.currentTimeMillis());
        VoiceClient.tick(mc);

        if (ClientState.openStatusNextTick && mc.screen == null) {
            ClientState.openStatusNextTick = false;
            mc.setScreen(new SystemScreen());
        }
        while (Keys.OPEN_STATUS.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new SystemScreen());
        }
        while (Keys.INSPECT.consumeClick()) {
            if (mc.crosshairPickEntity instanceof Player target && target != mc.player) {
                PacketDistributor.sendToServer(new Payloads.InspectRequest(target.getId()));
            } else {
                mc.player.displayClientMessage(Component.translatable("solostatus.inspect.no_target"), true);
            }
        }
        for (int i = 0; i < Keys.SKILLS.length; i++) {
            while (Keys.SKILLS[i].consumeClick()) {
                if (mc.screen == null && ClientState.data.slots[i] != null) {
                    PacketDistributor.sendToServer(new Payloads.CastSkill(i));
                }
            }
        }
    }

    /** Текстовый вариант голосовой команды: «Система, покажи статус» в чате открывает окно и не отправляется. */
    @SubscribeEvent
    public static void onChat(ClientChatEvent event) {
        boolean enabled;
        String phrase;
        try {
            enabled = SoloConfig.CHAT_PHRASE_ENABLED.get();
            phrase = SoloConfig.CHAT_PHRASE.get();
        } catch (Exception e) {
            return;
        }
        if (enabled && VoiceCommands.match(event.getMessage(), phrase) == VoiceCommands.Command.OPEN_STATUS) {
            event.setCanceled(true);
            ClientState.openStatusNextTick = true;
        }
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        VoiceClient.start();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
        VoiceClient.onLogout();
    }

    @SubscribeEvent
    public static void onClientCommands(RegisterClientCommandsEvent event) {
        VoiceClient.registerCommands(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        WeaponQuality q = QualitySystem.get(event.getItemStack());
        WeaponType type = WeaponClassifier.classify(event.getItemStack());
        if (q == null && (type == WeaponType.NONE || type == WeaponType.FIST)) return;
        List<Component> tip = event.getToolTip();
        int at = Math.min(1, tip.size());
        java.util.ArrayList<Component> lines = new java.util.ArrayList<>();
        if (q != null) {
            lines.add(Component.translatable("solostatus.tooltip.quality", q.tier().displayName()));
            if (q.damage() != 0) lines.add(stat("solostatus.tooltip.damage", q.damage()));
            if (q.speed() != 0) lines.add(stat("solostatus.tooltip.speed", q.speed()));
            if (q.crit() != 0) lines.add(stat("solostatus.tooltip.crit", q.crit()));
            if (q.broken()) lines.add(Component.translatable("solostatus.tooltip.broken").withStyle(ChatFormatting.DARK_RED));
            else if (q.condition() == WeaponQuality.DAMAGED) lines.add(Component.translatable("solostatus.tooltip.damaged").withStyle(ChatFormatting.RED));
        }
        if (type != WeaponType.NONE && type != WeaponType.FIST) {
            lines.add(Component.translatable("solostatus.tooltip.type", type.displayName()).withStyle(ChatFormatting.DARK_AQUA));
        }
        tip.addAll(at, lines);
    }

    private static Component stat(String key, float value) {
        String sign = value >= 0 ? "+" : "";
        return Component.translatable(key, sign + Math.round(value * 1000) / 10f + "%")
                .withStyle(value >= 0 ? ChatFormatting.BLUE : ChatFormatting.RED);
    }
}
