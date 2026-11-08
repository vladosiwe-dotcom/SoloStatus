package com.vladosiwe.solostatus.client.awakening;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The hunter's camera during the awakening scene: close above the face while the eyes open, rising as the head
 * lifts, swinging to the side while the hunter sits up, then behind the shoulder and back into the eyes, so the
 * scene ends exactly in normal first-person view.
 */
public final class AwakeningCamera {
    public record Pose(Vec3 pos, float yaw, float pitch, boolean detached) {}

    /** (time s, camera offset: forward, right, up), (look target offset: forward, right, up), relative to the feet. */
    private record Shot(double t, double cf, double cr, double cu, double lf, double lr, double lu) {}

    // Lying, the head is ~1 block behind the feet line at the hips (the body lies backwards), ~0.25 above ground.
    private static final Shot[] SHOTS = {
            new Shot(0.0, -0.85, 0.05, 1.35, -1.0, 0, 0.25),   // above the face, looking down
            new Shot(2.6, -0.80, 0.05, 1.05, -1.0, 0, 0.25),   // slowly closer while the eyes open
            new Shot(3.5, -0.20, 0.35, 1.25, -0.75, 0, 0.45),  // the head lifts: pull back toward the feet
            new Shot(5.5, 1.70, 1.10, 1.20, 0, 0, 1.05),       // sitting: from the front-side
            new Shot(7.0, -1.40, 0.55, 1.85, 1.5, 0, 1.45),    // standing: over the shoulder
    };

    private AwakeningCamera() {}

    private static double smooth(double a) {
        a = Mth.clamp(a, 0, 1);
        return a * a * (3 - 2 * a);
    }

    public static Pose pose(double t, double length, Vec3 feet, float sceneYaw, float viewYaw, float viewPitch, float eyeHeight) {
        double yr = Math.toRadians(sceneYaw);
        Vec3 f = new Vec3(-Math.sin(yr), 0, Math.cos(yr));   // where the hunter faced when the scene began
        Vec3 r = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));  // the hunter's right hand
        Vec3 up = new Vec3(0, 1, 0);

        Shot a = SHOTS[0], b = SHOTS[0];
        for (int i = 0; i < SHOTS.length; i++) {
            if (t >= SHOTS[i].t) { a = SHOTS[i]; b = i + 1 < SHOTS.length ? SHOTS[i + 1] : SHOTS[i]; }
        }
        double k = b.t == a.t ? 1 : smooth((t - a.t) / (b.t - a.t));
        Vec3 cam = feet.add(f.scale(Mth.lerp(k, a.cf, b.cf))).add(r.scale(Mth.lerp(k, a.cr, b.cr))).add(up.scale(Mth.lerp(k, a.cu, b.cu)));
        Vec3 look = feet.add(f.scale(Mth.lerp(k, a.lf, b.lf))).add(r.scale(Mth.lerp(k, a.lr, b.lr))).add(up.scale(Mth.lerp(k, a.lu, b.lu)));
        Vec3 d = look.subtract(cam);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));

        // last second: glide into the eyes and the player's own view direction
        double last = SHOTS[SHOTS.length - 1].t;
        if (t > last) {
            double m = smooth((t - last) / (length - last));
            Vec3 eye = feet.add(0, eyeHeight, 0);
            cam = cam.lerp(eye, m);
            yaw = yaw + Mth.wrapDegrees(viewYaw - yaw) * (float) m;
            pitch = Mth.lerp((float) m, pitch, viewPitch);
            return new Pose(cam, yaw, pitch, m < 0.85);
        }
        return new Pose(cam, yaw, pitch, true);
    }
}
