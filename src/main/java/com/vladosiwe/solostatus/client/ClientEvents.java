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
            var hc = ClientState.data.hunterClass;
            if (hc != null && type.hasMastery()) {
                var prof = com.vladosiwe.solostatus.clazz.Proficiency.of(ClientState.data, type);
                lines.add(Component.translatable("solostatus.tooltip.proficiency", hc.displayName(), prof.displayName()).withStyle(ChatFormatting.GRAY));
                if (prof.damage != 0) lines.add(stat("solostatus.tooltip.damage", (float) prof.damage));
                if (prof.attackSpeed != 0 && type.melee) lines.add(stat("solostatus.tooltip.speed", (float) prof.attackSpeed));
            }
        }
        tip.addAll(at, lines);
    }

    /** Armour sets: the set, how many pieces are worn and its bonuses; any gear: the difference with what is worn. */
    @SubscribeEvent
    public static void onGearTooltip(ItemTooltipEvent event) {
        var player = event.getEntity();
        if (player == null) return;
        net.minecraft.world.item.ItemStack stack = event.getItemStack();
        List<Component> tip = event.getToolTip();
        if (stack.getItem() instanceof com.vladosiwe.solostatus.registry.SystemArmor.Piece piece) {
            var set = piece.set();
            int worn = com.vladosiwe.solostatus.registry.SetBonuses.pieces(player, set);
            tip.add(Component.translatable("solostatus.tooltip.set", Component.translatable("solostatus.set." + set.key), worn).withStyle(ChatFormatting.GOLD));
            for (int need : new int[]{2, 4}) {
                tip.add(Component.literal(" " + need + ": ").append(Component.translatable("solostatus.set." + set.key + "." + need))
                        .withStyle(worn >= need ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
            }
        }
        // comparison with what is worn in the same slot (not for the worn item itself)
        net.minecraft.world.entity.EquipmentSlot slot = stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor ? armor.getEquipmentSlot()
                : hasAttack(stack) ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : null;
        if (slot == null) return;
        net.minecraft.world.item.ItemStack worn = player.getItemBySlot(slot);
        if (worn.isEmpty() || worn == stack || net.minecraft.world.item.ItemStack.matches(worn, stack)) return;
        java.util.ArrayList<Component> diff = new java.util.ArrayList<>();
        var attrs = new java.util.LinkedHashMap<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, String>();
        attrs.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, "solostatus.tooltip.cmp.damage");
        attrs.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, "solostatus.tooltip.cmp.speed");
        attrs.put(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, "solostatus.tooltip.cmp.armor");
        attrs.put(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS, "solostatus.tooltip.cmp.toughness");
        attrs.forEach((attr, key) -> {
            double d = flat(stack, slot, attr) - flat(worn, slot, attr);
            if (Math.abs(d) < 0.01) return;
            String v = (d > 0 ? "+" : "") + Math.round(d * 100) / 100.0;
            diff.add(Component.literal("  ").append(Component.translatable(key, v)).withStyle(d > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        });
        if (!diff.isEmpty()) {
            tip.add(Component.translatable("solostatus.tooltip.cmp", worn.getHoverName()).withStyle(ChatFormatting.GRAY));
            tip.addAll(diff);
        }
    }

    private static boolean hasAttack(net.minecraft.world.item.ItemStack stack) {
        return flat(stack, net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != 0;
    }

    /** The flat (ADD_VALUE) amount an item gives to an attribute in a slot. */
    private static double flat(net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot,
                               net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr) {
        double[] sum = {0};
        stack.forEachModifier(slot, (a, m) -> {
            if (a.equals(attr) && m.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) sum[0] += m.amount();
        });
        return sum[0];
    }

    private static Component stat(String key, float value) {
        String sign = value >= 0 ? "+" : "";
        return Component.translatable(key, sign + Math.round(value * 1000) / 10f + "%")
                .withStyle(value >= 0 ? ChatFormatting.BLUE : ChatFormatting.RED);
    }
}
