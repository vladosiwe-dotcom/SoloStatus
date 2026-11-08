package com.vladosiwe.solostatus.mixin;

import com.vladosiwe.solostatus.client.awakening.AwakeningCamera;
import com.vladosiwe.solostatus.client.awakening.AwakeningClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During the awakening scene the hunter's camera follows the scene's path (AwakeningCamera) instead of the eyes.
 * Marking the camera detached makes Minecraft draw the hunter's own body, as in third person.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;

    @Shadow protected abstract void setPosition(Vec3 pos);

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void solostatus$awakeningCamera(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        AwakeningCamera.Pose pose = AwakeningClient.cameraPose(entity, partialTick);
        if (pose == null) return;
        this.setRotation(pose.yaw(), pose.pitch());
        this.setPosition(pose.pos());
        this.detached = pose.detached();
    }
}
