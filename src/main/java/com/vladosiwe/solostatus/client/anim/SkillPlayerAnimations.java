package com.vladosiwe.solostatus.client.anim;

import com.vladosiwe.solostatus.SoloStatus;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Plays a skill's own body animation (assets/solostatus/player_animations/skills.json, made by
 * tools/animations/make_skill_animations.py) on a player's model through playerAnimator, the animation library Iron's
 * Spells brings and animates its casts with. Every player gets one layer of the System's above vanilla's; a new skill
 * replaces the animation in it. Reached by reflection, as the other integrations: if the library is missing or its
 * API differs, skills simply have no body animation (and a log line says so). In Epic Fight's battle mode Epic Fight
 * draws the player with its own animations, the skill's Epic Fight one among them.
 */
public final class SkillPlayerAnimations {
    private static final int PRIORITY = 1500; // above vanilla and Iron's cast layer
    private static final Map<AbstractClientPlayer, Object> LAYERS = new WeakHashMap<>();
    private static boolean ready, failed;
    private static Method getAnimation, getStack, addLayer, setAnimation, playAnimation;
    private static Constructor<?> newLayer, newPlayer;
    private static Class<?> keyframeAnimation;

    private SkillPlayerAnimations() {}

    private static boolean init() {
        if (ready || failed) return ready;
        try {
            Class<?> registry = Class.forName("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry");
            Class<?> access = Class.forName("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess");
            Class<?> stack = Class.forName("dev.kosmx.playerAnim.api.layered.AnimationStack");
            Class<?> animation = Class.forName("dev.kosmx.playerAnim.api.layered.IAnimation");
            Class<?> modifierLayer = Class.forName("dev.kosmx.playerAnim.api.layered.ModifierLayer");
            getAnimation = registry.getMethod("getAnimation", ResourceLocation.class);
            getStack = access.getMethod("getPlayerAnimLayer", AbstractClientPlayer.class);
            addLayer = stack.getMethod("addAnimLayer", int.class, animation);
            newLayer = modifierLayer.getConstructor();
            setAnimation = modifierLayer.getMethod("setAnimation", animation);
            try { // releases up to 2.0.x: the registry holds KeyframeAnimation, played by a KeyframeAnimationPlayer
                keyframeAnimation = Class.forName("dev.kosmx.playerAnim.core.data.KeyframeAnimation");
                newPlayer = Class.forName("dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer").getConstructor(keyframeAnimation);
            } catch (ReflectiveOperationException ignored) {
                // later versions: the registry holds IPlayable, which makes its own player
            }
            try {
                playAnimation = Class.forName("dev.kosmx.playerAnim.api.IPlayable").getMethod("playAnimation");
            } catch (ReflectiveOperationException ignored) {
            }
            ready = true;
        } catch (Throwable t) {
            failed = true;
            SoloStatus.LOGGER.warn("Skill body animations are off: playerAnimator is not available ({})", t.toString());
        }
        return ready;
    }

    public static void play(AbstractClientPlayer player, String skill) {
        if (!init()) return;
        try {
            Object anim = getAnimation.invoke(null, SoloStatus.id("skill_" + skill));
            if (anim == null) return;
            Object playing = keyframeAnimation != null && keyframeAnimation.isInstance(anim) ? newPlayer.newInstance(anim)
                    : playAnimation != null ? playAnimation.invoke(anim) : null;
            if (playing == null) return;
            Object layer = LAYERS.get(player);
            if (layer == null) {
                layer = newLayer.newInstance();
                addLayer.invoke(getStack.invoke(null, player), PRIORITY, layer);
                LAYERS.put(player, layer);
            }
            setAnimation.invoke(layer, playing);
        } catch (Throwable t) {
            failed = true;
            ready = false;
            SoloStatus.LOGGER.warn("Skill body animations are off: {}", t.toString());
        }
    }
}
