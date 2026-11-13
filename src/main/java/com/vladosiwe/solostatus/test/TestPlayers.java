package com.vladosiwe.solostatus.test;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Test hunters. A FakePlayer instead of the vanilla mock player: the mock player joins the player list, and other
 * mods (Epic Fight) then try to send it packets it cannot receive, which breaks the test for reasons unrelated
 * to this mod. A FakePlayer never joins the list, so only this mod's logic runs.
 */
final class TestPlayers {
    private static final AtomicInteger N = new AtomicInteger();

    private TestPlayers() {}

    static ServerPlayer create(GameTestHelper h) {
        int n = N.incrementAndGet();
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("solo-test-" + n).getBytes()), "test_hunter_" + n));
        p.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 1.5)));
        return p;
    }
}
