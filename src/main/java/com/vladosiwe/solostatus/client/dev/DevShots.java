package com.vladosiwe.solostatus.client.dev;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.clazz.ClassSystem;
import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.client.fp.FirstPersonAnimations;
import com.vladosiwe.solostatus.client.ui.SystemScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.io.File;

/**
 * Developer screenshot run, off unless the environment variable SOLO_DEVSHOTS=1 is set (players never trigger it).
 * Creates a flat creative world, puts a zombie and a wither skeleton in front of the player and hurts them (health
 * bars), opens the System window on its tabs, then swings each weapon type and draws a bow, saving frames to
 * screenshots/dev-*.png. Quits the game at the end, so it can run unattended under a virtual display.
 */
@EventBusSubscriber(modid = SoloStatus.MODID, value = Dist.CLIENT)
public final class DevShots {
    private static final boolean ON = "1".equals(System.getenv("SOLO_DEVSHOTS"));
    private static boolean worldAsked;
    private static int tick = -1;
    private static String frameName;
    private static int framesLeft;
    private static int frameIndex;

    private DevShots() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            if (!worldAsked && mc.screen instanceof TitleScreen) {
                worldAsked = true;
                LevelSettings settings = new LevelSettings("solo-devshots-" + System.currentTimeMillis(), GameType.CREATIVE, false,
                        Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, new WorldOptions(1L, false, false),
                        reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                        mc.screen);
            }
            return;
        }
        tick++;
        if (tick == 1) {
            mc.getWindow().setWindowed(1280, 720);
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
        }
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
        if (sp == null) return;
        switch (tick) {
            case 40 -> server.execute(() -> {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule doMobSpawning false");
                ClassSystem.chooseClass(sp, "mage".equals(System.getenv("SOLO_DEVSHOTS_CLASS")) ? HunterClass.MAGE : HunterClass.WARRIOR);
                com.vladosiwe.solostatus.level.LevelSystem.grantXp(sp, 400000, com.vladosiwe.solostatus.level.XpReason.COMMAND); // open the tiers
                if (!"mage".equals(System.getenv("SOLO_DEVSHOTS_CLASS"))) com.vladosiwe.solostatus.clazz.ClassSystem.chooseSpec(sp, com.vladosiwe.solostatus.clazz.Specialization.DUELIST);
                sp.setYRot(0); sp.setXRot(10);
                sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), 0, 8);
                spawn(sp, EntityType.ZOMBIE, -1.6);
                spawn(sp, EntityType.WITHER_SKELETON, 1.6);
                // jewellery: a legendary ring and an epic amulet worn, a rare ring left in the hotbar
                for (var kind : new com.vladosiwe.solostatus.accessory.AccessoryKind[]{com.vladosiwe.solostatus.accessory.AccessoryKind.RING,
                        com.vladosiwe.solostatus.accessory.AccessoryKind.AMULET}) {
                    ItemStack jewel = new ItemStack(kind == com.vladosiwe.solostatus.accessory.AccessoryKind.RING
                            ? com.vladosiwe.solostatus.registry.ModItems.RING.get() : com.vladosiwe.solostatus.registry.ModItems.AMULET.get());
                    com.vladosiwe.solostatus.accessory.AccessorySystem.apply(jewel, com.vladosiwe.solostatus.accessory.AccessorySystem.roll(kind,
                            kind == com.vladosiwe.solostatus.accessory.AccessoryKind.RING ? com.vladosiwe.solostatus.quality.QualityRarity.LEGENDARY
                                    : com.vladosiwe.solostatus.quality.QualityRarity.EPIC, sp.getRandom()));
                    sp.setItemInHand(InteractionHand.MAIN_HAND, jewel);
                    com.vladosiwe.solostatus.accessory.AccessorySystem.equipFromHand(sp, InteractionHand.MAIN_HAND);
                }
                ItemStack spare = new ItemStack(com.vladosiwe.solostatus.registry.ModItems.RING.get());
                com.vladosiwe.solostatus.accessory.AccessorySystem.apply(spare, com.vladosiwe.solostatus.accessory.AccessorySystem.roll(
                        com.vladosiwe.solostatus.accessory.AccessoryKind.RING, com.vladosiwe.solostatus.quality.QualityRarity.RARE, sp.getRandom()));
                sp.getInventory().setItem(1, spare);
                sp.getInventory().setItem(2, new ItemStack(com.vladosiwe.solostatus.registry.ModItems.AMULET.get()));
                sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                var hunter = com.vladosiwe.solostatus.data.Hunter.get(sp); // a modified skill for the skill card frame
                hunter.skills.put("whirlwind", 3);
                hunter.skillMods.put("whirlwind", 1);
                hunter.markDirty();
            });
            case 150 -> server.execute(() -> sp.serverLevel().getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(8),
                    e -> e != sp).forEach(e -> e.hurt(sp.damageSources().playerAttack(sp), 6)));
            case 154 -> mc.gui.getChat().clearMessages(false);
            case 156 -> shot(mc, "health-bars");
            case 200 -> mc.setScreen(new SystemScreen(SystemScreen.Win.STATUS));
            case 250 -> shot(mc, "status");
            case 255 -> mc.setScreen(new SystemScreen(SystemScreen.Win.MASTERY));
            case 305 -> shot(mc, "mastery");
            case 310 -> mc.setScreen(new SystemScreen(SystemScreen.Win.SKILLS));
            case 360 -> shot(mc, "skills");
            case 362 -> mc.setScreen(new SystemScreen(SystemScreen.Win.SKILLS).select("whirlwind"));
            case 385 -> shot(mc, "skill-mods");
            case 388 -> mc.setScreen(null);
            default -> {}
        }
        // weapon swings: one every 40 ticks from tick 390
        net.minecraft.world.item.Item[] weapons = {Items.IRON_SWORD, com.vladosiwe.solostatus.registry.LegendaryWeapons.Kind.MOUNTAIN_CLEAVER.item(), com.vladosiwe.solostatus.registry.LegendaryWeapons.Kind.DUSK_FANG.item(),
                com.vladosiwe.solostatus.registry.LegendaryWeapons.Kind.STARWELL_STAFF.item(), com.vladosiwe.solostatus.registry.LegendaryWeapons.Kind.DAWN_SCEPTER.item()};
        String[] names = {"sword", "mountain_cleaver", "dusk_fang", "starwell_staff", "dawn_scepter"};
        for (int i = 0; i < weapons.length; i++) {
            int at = 390 + i * 40;
            if (tick == at) {
                ItemStack st = new ItemStack(weapons[i]);
                server.execute(() -> sp.setItemInHand(InteractionHand.MAIN_HAND, st));
            }
            if (tick == at + 12) {
                FirstPersonAnimations.startAttack();
                frames("swing-" + names[i], 14);
            }
        }
        int bowAt = 390 + weapons.length * 40;
        if (tick == bowAt) server.execute(() -> {
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(com.vladosiwe.solostatus.registry.LegendaryWeapons.Kind.SKYHUNT_BOW.item())); // needs no arrows
        });
        if (tick == bowAt + 10) mc.options.keyUse.setDown(true);
        if (tick == bowAt + 20) shot(mc, "bow-drawing");
        if (tick == bowAt + 38) shot(mc, "bow-full");
        if (tick == bowAt + 40) { mc.options.keyUse.setDown(false); frames("bow-release", 8); }
        // first-person motions of skills, one of each kind
        String[] skills = {"power_strike", "charge", "defensive_stance", "war_cry", "arcane_blast", "titan_slash"};
        int skillAt = bowAt + 70;
        for (int i = 0; i < skills.length; i++) {
            int at = skillAt + i * 45;
            if (tick == at) server.execute(() -> sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)));
            if (tick == at + 8) {
                FirstPersonAnimations.playSkill(skills[i]);
                frames("skill-" + skills[i], 16);
            }
        }
        // the skills' body animations, seen from the front with an empty hand (Epic Fight's battle mode is off then)
        String[] bodyAnims = {"war_cry", "whirlwind", "devastating_blow", "flurry", "concealment", "defensive_stance",
                "arrow_volley", "sanctuary", "earthbreaker", "air_step", "battle_hymn", "riposte"};
        int animAt = skillAt + skills.length * 45 + 10;
        for (int i = 0; i < bodyAnims.length; i++) {
            int at = animAt + i * 30;
            if (tick == at) {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                server.execute(() -> sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
            }
            if (tick == at + 4) com.vladosiwe.solostatus.client.anim.SkillPlayerAnimations.play(mc.player, bodyAnims[i]);
            if (tick == at + 9) shot(mc, "anim-" + bodyAnims[i] + "-a");
            if (tick == at + 15) shot(mc, "anim-" + bodyAnims[i] + "-b");
        }

        // the legendary weapons in hand, seen from the front in third person
        int armorAt = animAt + bodyAnims.length * 30 + 10;
        var legendary = com.vladosiwe.solostatus.registry.LegendaryWeapons.Kind.values();
        for (int i = 0; i < legendary.length; i++) {
            int at = armorAt + i * 25;
            var kind = legendary[i];
            if (tick == at) {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                server.execute(() -> sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(kind.item())));
            }
            if (tick == at + 15) shot(mc, "legendary-" + kind.key);
        }
        armorAt += legendary.length * 25;
        if (tick == armorAt) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        // the rank and history windows and an achievement section
        int windowsAt = armorAt + 20;
        if (tick == windowsAt) mc.setScreen(new SystemScreen(SystemScreen.Win.STATUS).showOverlay("RANK"));
        if (tick == windowsAt + 40) shot(mc, "rank");
        if (tick == windowsAt + 45) mc.setScreen(new SystemScreen(SystemScreen.Win.STATUS).showOverlay("HISTORY"));
        if (tick == windowsAt + 85) shot(mc, "history");
        if (tick == windowsAt + 90) mc.setScreen(new SystemScreen(SystemScreen.Win.ACHIEVEMENTS));
        if (tick == windowsAt + 130) shot(mc, "achievements");
        if (tick == windowsAt + 135) mc.setScreen(null);
        if (tick == windowsAt + 145) {
            SoloStatus.LOGGER.info("DevShots: done");
            mc.stop();
        }
    }

    private static void spawn(ServerPlayer sp, EntityType<? extends Mob> type, double side) {
        Mob mob = type.create(sp.serverLevel());
        if (mob == null) return;
        Vec3 at = sp.position().add(side, 0, 4.5);
        mob.moveTo(at.x, at.y, at.z, 180, 0);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        sp.serverLevel().addFreshEntity(mob);
    }

    private static void shot(Minecraft mc, String name) {
        frames(name, 1);
    }

    private static void frames(String name, int count) {
        frameName = name;
        framesLeft = count;
        frameIndex = 0;
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        if (!ON || framesLeft <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        String file = framesLeft == 1 && frameIndex == 0 ? "dev-" + frameName + ".png" : String.format("dev-%s-%02d.png", frameName, frameIndex);
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), msg -> {});
        frameIndex++;
        framesLeft--;
    }

}
