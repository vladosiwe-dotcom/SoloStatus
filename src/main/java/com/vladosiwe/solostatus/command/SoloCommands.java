package com.vladosiwe.solostatus.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.vladosiwe.solostatus.combat.WeaponClassifier;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.level.LevelSystem;
import com.vladosiwe.solostatus.mastery.MasterySystem;
import com.vladosiwe.solostatus.network.Notices;
import com.vladosiwe.solostatus.quality.QualityRarity;
import com.vladosiwe.solostatus.quality.QualitySystem;
import com.vladosiwe.solostatus.quality.WeaponQuality;
import com.vladosiwe.solostatus.registry.ModAttachments;
import com.vladosiwe.solostatus.stats.AttributeSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

/**
 * /solostatus — административные команды (уровень прав 2) для тестирования и настройки серверов.
 */
public final class SoloCommands {
    private SoloCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("solostatus")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("awaken").then(Commands.argument("targets", EntityArgument.players())
                        .executes(c -> each(c, com.vladosiwe.solostatus.awakening.AwakeningScene::start))))
                .then(Commands.literal("xp").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(c -> each(c, p -> LevelSystem.grantXp(p, IntegerArgumentType.getInteger(c, "amount"), com.vladosiwe.solostatus.level.XpReason.COMMAND))))))
                .then(Commands.literal("level").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, PlayerData.MAX_LEVEL))
                                .executes(c -> each(c, p -> LevelSystem.setLevel(p, IntegerArgumentType.getInteger(c, "level")))))))
                .then(Commands.literal("statpoints").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                .executes(c -> each(c, p -> {
                                    PlayerData d = Hunter.get(p);
                                    d.freeStatPoints = Math.max(0, d.freeStatPoints + IntegerArgumentType.getInteger(c, "amount"));
                                    d.markDirty();
                                })))))
                .then(Commands.literal("skillpoints").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                .executes(c -> each(c, p -> {
                                    PlayerData d = Hunter.get(p);
                                    d.skillPoints = Math.max(0, d.skillPoints + IntegerArgumentType.getInteger(c, "amount"));
                                    d.markDirty();
                                })))))
                .then(Commands.literal("mastery").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("key", StringArgumentType.string())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(c -> each(c, p -> {
                                            String key = StringArgumentType.getString(c, "key");
                                            MasterySystem.add(p, Hunter.get(p), key, DoubleArgumentType.getDouble(c, "amount"), Component.literal(key));
                                        }))))))
                .then(Commands.literal("counter").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("key", StringArgumentType.string())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(c -> each(c, p -> Hunter.get(p).addCounter(StringArgumentType.getString(c, "key"),
                                                IntegerArgumentType.getInteger(c, "amount"))))))))
                .then(Commands.literal("reset").then(Commands.argument("targets", EntityArgument.players())
                        .executes(c -> each(c, p -> {
                            p.setData(ModAttachments.PLAYER_DATA, new PlayerData());
                            AttributeSystem.apply(p);
                            Notices.forceSync(p);
                        }))))
                .then(Commands.literal("quality").then(Commands.argument("rarity", IntegerArgumentType.integer(0, 4))
                        .executes(SoloCommands::quality))));
    }

    private interface PlayerAction {
        void run(ServerPlayer p) throws CommandSyntaxException;
    }

    private static int each(CommandContext<CommandSourceStack> c, PlayerAction action) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
        for (ServerPlayer p : players) {
            action.run(p);
            Hunter.get(p).markDirty();
        }
        c.getSource().sendSuccess(() -> Component.translatable("solostatus.command.done", players.size()), true);
        return players.size();
    }

    /** Перебросить качество оружия в руке с заданной редкостью (для тестов баланса). */
    private static int quality(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack stack = p.getMainHandItem();
        if (!WeaponClassifier.isQualityWeapon(stack)) {
            c.getSource().sendFailure(Component.translatable("solostatus.command.not_weapon"));
            return 0;
        }
        QualityRarity r = QualityRarity.byIndex(IntegerArgumentType.getInteger(c, "rarity"));
        float dmg = (r.minDamage + r.maxDamage) / 2;
        QualitySystem.apply(stack, new WeaponQuality(r.ordinal(), dmg, 0.02f * r.ordinal(), 0.02f * r.ordinal(), WeaponQuality.PRISTINE, false));
        c.getSource().sendSuccess(() -> Component.translatable("solostatus.command.done", 1), false);
        return 1;
    }
}
