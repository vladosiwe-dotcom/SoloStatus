package com.vladosiwe.solostatus.client.awakening;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * First-person camera of the awakening scene: it sits in the eyes of the scene model and follows its head, so
 * the hunter sees the sky while lying, the own chest and legs when the head lifts, the world while sitting up
 * and standing. In the last half second it blends into the player's normal view.
 *
 * The head transform is computed from the same keyframes GeckoLib plays (AwakeningTimeline), with GeckoLib's
 * conventions: a bone moves by (-x, y, z)/16, rotates around its pivot in Z, Y, X order, X and Y negated.
 */
public final class AwakeningCamera {
    public record Pose(Vec3 pos, float yaw, float pitch, boolean detached) {}

    private static final float PX = 1 / 16f;
    /** Root pivot (hips) and head pivot (neck) of the model, in pixels. */
    private static final float ROOT_PIVOT = 12, HEAD_PIVOT = 24;
    /** Eye point: middle of the eye row, just in front of the face. */
    private static final Vector3f EYE = new Vector3f(0, 28 * PX, -4.6f * PX);
    private static final double BLEND = 0.5;

    private AwakeningCamera() {}

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }

    /** Model-to-world transform of the head bone at scene time t (without the feet translation). */
    static Matrix4f headTransform(double t, float sceneYaw) {
        float[] rp = AwakeningTimeline.at("root.position", t);
        float[] rr = AwakeningTimeline.at("root.rotation", t);
        float[] hr = AwakeningTimeline.at("head.rotation", t);
        Matrix4f m = new Matrix4f().rotateY(rad(180f - sceneYaw));
        m.translate(-rp[0] * PX, rp[1] * PX, rp[2] * PX)
                .translate(0, ROOT_PIVOT * PX, 0)
                .rotateZ(rad(rr[2])).rotateY(rad(-rr[1])).rotateX(rad(-rr[0]))
                .translate(0, -ROOT_PIVOT * PX, 0);
        m.translate(0, HEAD_PIVOT * PX, 0)
                .rotateZ(rad(hr[2])).rotateY(rad(-hr[1])).rotateX(rad(-hr[0]))
                .translate(0, -HEAD_PIVOT * PX, 0);
        return m;
    }

    public static Pose pose(double t, double length, Vec3 feet, float sceneYaw, float viewYaw, float viewPitch, float eyeHeight) {
        Matrix4f m = headTransform(t, sceneYaw);
        Vector3f eye = m.transformPosition(new Vector3f(EYE));
        Vector3f fwd = m.transformDirection(new Vector3f(0, 0, -1)).normalize();
        Vector3f up = m.transformDirection(new Vector3f(0, 1, 0)).normalize();
        // Looking straight up or down the yaw is undefined: the top of the picture follows the top of the head.
        float hx = fwd.x - up.x * fwd.y, hz = fwd.z - up.z * fwd.y;
        float yaw = (float) Math.toDegrees(Math.atan2(-hx, hz));
        float pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(fwd.y, -1, 1)));
        Vec3 cam = feet.add(eye.x, eye.y, eye.z);

        double k = Mth.clamp((t - (length - BLEND)) / BLEND, 0, 1);
        if (k > 0) {
            k = k * k * (3 - 2 * k);
            cam = cam.lerp(feet.add(0, eyeHeight, 0), k);
            yaw += Mth.wrapDegrees(viewYaw - yaw) * (float) k;
            pitch = Mth.lerp((float) k, pitch, viewPitch);
        }
        // The body (without its head) is drawn while the scene moves the camera; in the last moments it hands over
        // to the normal first-person view.
        return new Pose(cam, yaw, pitch, k < 0.9);
    }
}
