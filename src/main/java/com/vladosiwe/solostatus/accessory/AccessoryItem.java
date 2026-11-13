package com.vladosiwe.solostatus.accessory;

import com.vladosiwe.solostatus.quality.QualityRarity;
import com.vladosiwe.solostatus.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A ring or an amulet. Its bonuses live in the solostatus:accessory component, rolled when it drops.
 * Use (right click) puts it into the System's own jewellery slots; with Curios installed it also fits the
 * Curios ring / necklace slots. Either way at most 2 rings and 1 amulet count.
 */
public class AccessoryItem extends Item {
    public final AccessoryKind kind;

    public AccessoryItem(AccessoryKind kind, Properties properties) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) AccessorySystem.equipFromHand(sp, hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        AccessoryData data = stack.get(ModComponents.ACCESSORY.get());
        Component base = super.getName(stack);
        return data == null ? base : base.copy().withStyle(data.tier().format);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        AccessoryData data = stack.get(ModComponents.ACCESSORY.get());
        return data != null && data.tier() == QualityRarity.LEGENDARY;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        AccessoryData data = stack.get(ModComponents.ACCESSORY.get());
        if (data == null) {
            lines.add(Component.translatable("solostatus.accessory.unrolled").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("solostatus.tooltip.quality", data.tier().displayName()));
            for (AccessoryData.Roll r : data.rolls()) {
                Affix a = Affix.byKey(r.affix());
                if (a != null) lines.add(Component.literal(a.format(r.value()) + " ").append(a.displayName()).withStyle(ChatFormatting.BLUE));
            }
        }
        lines.add(Component.translatable("solostatus.accessory.wear_hint." + kind.key).withStyle(ChatFormatting.DARK_GRAY));
    }
}
