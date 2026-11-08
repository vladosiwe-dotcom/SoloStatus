package com.vladosiwe.solostatus.client.awakening;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vladosiwe.solostatus.SoloStatus;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The scene's keyframes in scene time, read from the same animation file GeckoLib plays, so the camera and the
 * fallback (vanilla model, no GeckoLib) move in step with the animated model.
 */
public final class AwakeningTimeline {
    public static final String[] PHASES = {"awakening.lie", "awakening.open_eyes", "awakening.raise_head", "awakening.sit_up", "awakening.stand_up"};
    /** Scene time (s) at which each phase starts; filled from the file. */
    public static final double[] PHASE_START = new double[PHASES.length + 1];

    private record Key(double t, float[] v) {}

    private static Map<String, List<Key>> tracks;

    private AwakeningTimeline() {}

    private static synchronized void load() {
        if (tracks != null) return;
        tracks = new HashMap<>();
        try (Reader r = Minecraft.getInstance().getResourceManager()
                .openAsReader(SoloStatus.id("animations/awakening/player.animation.json"))) {
            JsonObject anims = JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("animations");
            double offset = 0;
            for (int i = 0; i < PHASES.length; i++) {
                JsonObject a = anims.getAsJsonObject(PHASES[i]);
                PHASE_START[i] = offset;
                for (var bone : a.getAsJsonObject("bones").entrySet()) {
                    for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
                        List<Key> keys = tracks.computeIfAbsent(bone.getKey() + "." + channel.getKey(), k -> new ArrayList<>());
                        for (var kf : channel.getValue().getAsJsonObject().entrySet()) {
                            var arr = kf.getValue().getAsJsonArray();
                            keys.add(new Key(offset + Double.parseDouble(kf.getKey()),
                                    new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()}));
                        }
                    }
                }
                offset += a.get("animation_length").getAsDouble();
            }
            PHASE_START[PHASES.length] = offset;
        } catch (Exception e) {
            SoloStatus.LOGGER.warn("Awakening: cannot read the scene keyframes: {}", e.toString());
        }
    }

    /** Linear value of bone.channel ("root.rotation") at scene time t; zeros if unknown. */
    public static float[] at(String track, double t) {
        load();
        List<Key> keys = tracks.get(track);
        if (keys == null || keys.isEmpty()) return new float[3];
        if (t <= keys.get(0).t) return keys.get(0).v.clone();
        for (int i = 1; i < keys.size(); i++) {
            Key a = keys.get(i - 1), b = keys.get(i);
            if (t <= b.t) {
                float f = b.t == a.t ? 1f : (float) ((t - a.t) / (b.t - a.t));
                return new float[]{a.v[0] + (b.v[0] - a.v[0]) * f, a.v[1] + (b.v[1] - a.v[1]) * f, a.v[2] + (b.v[2] - a.v[2]) * f};
            }
        }
        return keys.get(keys.size() - 1).v.clone();
    }

    /** Index of the phase playing at scene time t. */
    public static int phaseAt(double t) {
        load();
        for (int i = PHASES.length - 1; i >= 0; i--) if (t >= PHASE_START[i]) return i;
        return 0;
    }
}
