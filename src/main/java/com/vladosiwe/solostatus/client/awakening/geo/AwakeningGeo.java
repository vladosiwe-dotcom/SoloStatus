package com.vladosiwe.solostatus.client.awakening.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.client.awakening.AwakeningTimeline;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * GeckoLib part of the awakening scene (loaded only when GeckoLib is installed).
 *
 * The hunter is drawn as a player-shaped GeckoLib model (assets/solostatus/geo/awakening/player_{wide,slim}.geo.json,
 * made from art/blockbench/*.bbmodel) wearing the player's own skin: the model uses the standard 64x64 skin layout
 * and its texture is the skin Minecraft already loaded for that player, wide or slim arms as the skin says.
 * Nothing about the player is replaced permanently: the model exists only while the scene plays, and the eyelids
 * take their colour from the brow row of the same skin.
 */
public final class AwakeningGeo {
    private static final ResourceLocation MODEL_WIDE = SoloStatus.id("geo/awakening/player_wide.geo.json");
    private static final ResourceLocation MODEL_SLIM = SoloStatus.id("geo/awakening/player_slim.geo.json");
    private static final ResourceLocation ANIMATIONS = SoloStatus.id("animations/awakening/player.animation.json");

    /** One animated "puppet" per scene (entity id + scene start), so a replay restarts the animations. */
    private static final Map<Long, Puppet> PUPPETS = new HashMap<>();
    private static Renderer renderer;
    private static boolean announced;

    private AwakeningGeo() {}

    /**
     * Draws the hunter's scene at the entity's render origin.
     *
     * @param sceneKey  unique per scene
     * @param elapsedAtStart scene time (s) already passed when this client learned about the scene (late viewers)
     */
    public static void render(AbstractClientPlayer player, long sceneKey, double elapsedAtStart, float bodyYaw, boolean withoutHead,
                              PoseStack poseStack, MultiBufferSource buffers, int light, float partialTick) {
        if (renderer == null) renderer = new Renderer();
        PlayerSkin skin = player.getSkin();
        Puppet puppet = PUPPETS.computeIfAbsent(sceneKey, k -> new Puppet(AwakeningTimeline.phaseAt(elapsedAtStart)));
        puppet.skin = skin.texture();
        puppet.slim = skin.model() == PlayerSkin.Model.SLIM;
        puppet.withoutHead = withoutHead;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw)); // model front (-Z) faces where the hunter looked
        renderer.render(poseStack, puppet, buffers, null, null, light, partialTick);
        poseStack.popPose();
        if (!announced) {
            announced = true;
            SoloStatus.LOGGER.info("Awakening: drawn with GeckoLib ({} model, skin {})", puppet.slim ? "slim" : "wide", puppet.skin);
        }
    }

    public static void forget(long sceneKey) {
        PUPPETS.remove(sceneKey);
    }

    /** The animated object GeckoLib drives. Starts from the phase the scene is in (a late viewer skips what passed). */
    static final class Puppet implements GeoAnimatable {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this, false);
        private final RawAnimation sequence;
        ResourceLocation skin;
        boolean slim;
        /** The hunter's own first-person view: the camera is in the head, so the head is not drawn. */
        boolean withoutHead;

        Puppet(int fromPhase) {
            RawAnimation a = RawAnimation.begin();
            String[] phases = AwakeningTimeline.PHASES;
            for (int i = fromPhase; i < phases.length - 1; i++) a = a.thenPlay(phases[i]);
            this.sequence = a.thenPlayAndHold(phases[phases.length - 1]);
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "awakening", 0, state -> state.setAndContinue(sequence)));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object object) {
            return RenderUtil.getCurrentTick();
        }
    }

    static final class Model extends GeoModel<Puppet> {
        @Override
        public ResourceLocation getModelResource(Puppet p) {
            return p.slim ? MODEL_SLIM : MODEL_WIDE;
        }

        @Override
        public ResourceLocation getTextureResource(Puppet p) {
            return p.skin;
        }

        @Override
        public ResourceLocation getAnimationResource(Puppet p) {
            return ANIMATIONS;
        }

        /** Skins have transparent outer layers (hat, jacket, sleeves). */
        @Override
        public RenderType getRenderType(Puppet p, ResourceLocation texture) {
            return RenderType.entityTranslucent(texture);
        }
    }

    static final class Renderer extends GeoObjectRenderer<Puppet> {
        Renderer() {
            super(new Model());
        }

        /** GeoObjectRenderer centres the model in a block (+0.5, +0.51, +0.5); here it stands where the player stands. */
        @Override
        public void preRender(PoseStack poseStack, Puppet animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                              @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            this.objectRenderTranslations = new Matrix4f(poseStack.last().pose());
            model.getBone("head").ifPresent(b -> {
                b.setHidden(animatable.withoutHead);
                b.setChildrenHidden(animatable.withoutHead);
            });
            scaleModelForRender(this.scaleWidth, this.scaleHeight, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
        }
    }
}
