package com.vladosiwe.solostatus.awakening;

import com.mojang.authlib.GameProfile;
import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.skill.Scheduler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Automated multiplayer check, used only by the client end-to-end run (server environment SOLO_E2E_BOT=1):
 * after the real player's own awakening, a second player — a bot with an in-memory connection — joins in front
 * of them and wakes up. The real client then sees another player's awakening exactly as any player would: the
 * server sends the scene to everyone who can see the bot, and the client draws the bot as the scene model.
 *
 * The bot's connection copies NeoForge's negotiated-channel attributes from the real player's connection, so
 * packets that mods send to every player who joins do not fail on it.
 */
final class E2eBot {
    static final boolean ON = "1".equals(System.getenv("SOLO_E2E_BOT"));
    private static final String[] NEOFORGE_CHANNEL_KEYS = {
            "neoforge:payload_setup", "neoforge:adhoc_channels", "neoforge:common_channels", "neoforge:connection_type"};
    private static boolean done;

    private E2eBot() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void wakeNear(ServerPlayer viewer) {
        if (done) return;
        done = true;
        try {
            MinecraftServer server = viewer.server;
            ServerLevel level = viewer.serverLevel();
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("solo-e2e-bot".getBytes(StandardCharsets.UTF_8)), "Hunter_Bot");
            ServerPlayer bot = new ServerPlayer(server, level, profile, ClientInformation.createDefault());

            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(connection);
            var real = viewer.connection.getConnection().channel();
            for (String key : NEOFORGE_CHANNEL_KEYS) {
                AttributeKey k = AttributeKey.valueOf(key);
                channel.attr(k).set(real.attr(k).get());
            }

            // 3.5 blocks in front of the viewer, lying across the view (side-on), the viewer turned to face it
            float yaw = viewer.getYRot();
            Vec3 look = Vec3.directionFromRotation(0, yaw);
            Vec3 at = viewer.position().add(look.scale(3.5));
            server.getPlayerList().placeNewPlayer(connection, bot, new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false));
            bot.teleportTo(level, at.x, viewer.getY(), at.z, yaw + 90, 0);
            bot.yBodyRot = bot.yBodyRotO = yaw + 90;
            viewer.connection.teleport(viewer.getX(), viewer.getY(), viewer.getZ(), yaw, 25);
            SoloStatus.LOGGER.info("Awakening e2e: {} joined in front of {}", profile.getName(), viewer.getGameProfile().getName());

            Scheduler.later(20, () -> AwakeningScene.start(bot));
            Scheduler.later(20 + AwakeningScene.DURATION + 30, () -> {
                bot.connection.disconnect(Component.literal("end-to-end check over"));
                SoloStatus.LOGGER.info("Awakening e2e: {} left", profile.getName());
            });
        } catch (Throwable t) {
            SoloStatus.LOGGER.error("Awakening e2e: could not add the second player", t);
        }
    }
}
