package dev.star1mp.giantrefresh.cinema;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import net.minecraft.world.phys.Vec3;

/**
 * Camera track for a shot: key frames (time in seconds) with position, direction and field of view.
 * Positions are interpolated with a Catmull-Rom spline (smooth through every key); a key can look
 * at a point that moves over time (an actor), which keeps the subject framed exactly.
 */
public final class Cam {
    public static final class Key {
        final double t;
        final Vec3 pos;
        DoubleFunction<Vec3> posF;
        float yaw;
        float pitch;
        DoubleFunction<Vec3> look; // overrides yaw/pitch
        float fov = 70.0F;
        boolean ease = true;

        Key(double t, Vec3 pos) {
            this.t = t;
            this.pos = pos;
        }

        public Key fov(float f) { fov = f; return this; }
        public Key linear() { ease = false; return this; }
    }

    public record Pose(Vec3 pos, float yaw, float pitch, float fov, float roll) {}

    private final List<Key> keys = new ArrayList<>();
    private DoubleFunction<Float> roll = t -> 0.0F;
    /** Hand-held wobble amplitude in degrees (0 = locked off). */
    private double handheld;
    private DoubleFunction<Double> shake = t -> 0.0;

    public Key at(double t, Vec3 pos, float yaw, float pitch) {
        Key k = new Key(t, pos);
        k.yaw = yaw;
        k.pitch = pitch;
        keys.add(k);
        return k;
    }

    public Key at(double t, Vec3 pos, DoubleFunction<Vec3> look) {
        Key k = new Key(t, pos);
        k.look = look;
        keys.add(k);
        return k;
    }

    public Key at(double t, Vec3 pos, Vec3 lookAt) {
        return at(t, pos, x -> lookAt);
    }

    /** A key whose position moves (follows an actor) and that looks at a moving point. */
    public Key follow(double t, DoubleFunction<Vec3> pos, DoubleFunction<Vec3> look) {
        Key k = new Key(t, Vec3.ZERO);
        k.posF = pos;
        k.look = look;
        keys.add(k);
        return k;
    }

    private static Vec3 p(Key k, double t) { return k.posF != null ? k.posF.apply(t) : k.pos; }

    public Cam handheld(double degrees) { handheld = degrees; return this; }
    public Cam shake(DoubleFunction<Double> s) { shake = s; return this; }
    public Cam roll(DoubleFunction<Float> r) { roll = r; return this; }

    public Pose pose(double t) {
        if (keys.isEmpty()) {
            return new Pose(Vec3.ZERO, 0, 0, 70, 0);
        }
        int i = 0;
        while (i < keys.size() - 2 && keys.get(i + 1).t <= t) {
            i++;
        }
        Key a = keys.get(i);
        Key b = keys.get(Math.min(i + 1, keys.size() - 1));
        double u = b.t > a.t ? Math.max(0, Math.min(1, (t - a.t) / (b.t - a.t))) : 0;
        if (t >= b.t) {
            u = 1;
        }
        double e = (a.ease && b.ease) ? smooth(u) : (a.ease ? easeOut(u) : (b.ease ? easeIn(u) : u));
        if (keys.size() == 1) {
            e = 0;
        }
        Key p0 = keys.get(Math.max(0, i - 1));
        Key p3 = keys.get(Math.min(keys.size() - 1, i + 2));
        Vec3 pos = catmull(p(p0, t), p(a, t), p(b, t), p(p3, t), e);
        float yawA = yawOf(a, pos, t);
        float yawB = yawOf(b, pos, t);
        float pitA = pitchOf(a, pos, t);
        float pitB = pitchOf(b, pos, t);
        float dy = wrap(yawB - yawA);
        float yaw = yawA + (float) (dy * e);
        float pitch = pitA + (float) ((pitB - pitA) * e);
        float fov = (float) (a.fov + (b.fov - a.fov) * e);
        if (handheld > 0) {
            yaw += (float) (handheld * (Math.sin(t * 0.83) * 0.6 + Math.sin(t * 1.71 + 1.3) * 0.4));
            pitch += (float) (handheld * 0.7 * (Math.sin(t * 0.97 + 0.5) * 0.6 + Math.sin(t * 2.03 + 2.2) * 0.4));
        }
        double s = shake.apply(t);
        if (s > 0) {
            yaw += (float) (s * (Math.sin(t * 37.0) * 0.6 + Math.sin(t * 23.0 + 1.3) * 0.4));
            pitch += (float) (s * (Math.sin(t * 41.0 + 0.7) * 0.6 + Math.sin(t * 29.0 + 2.1) * 0.4));
        }
        return new Pose(pos, yaw, pitch, fov, roll.apply(t));
    }

    private static float yawOf(Key k, Vec3 from, double t) {
        if (k.look == null) {
            return k.yaw;
        }
        Vec3 d = k.look.apply(t).subtract(from);
        return (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
    }

    private static float pitchOf(Key k, Vec3 from, double t) {
        if (k.look == null) {
            return k.pitch;
        }
        Vec3 d = k.look.apply(t).subtract(from);
        return (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
    }

    static float wrap(float a) {
        a %= 360.0F;
        if (a >= 180.0F) a -= 360.0F;
        if (a < -180.0F) a += 360.0F;
        return a;
    }

    static double smooth(double u) { return u * u * (3 - 2 * u); }
    static double easeIn(double u) { return u * u; }
    static double easeOut(double u) { return 1 - (1 - u) * (1 - u); }

    private static Vec3 catmull(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double u) {
        double u2 = u * u;
        double u3 = u2 * u;
        return new Vec3(
                cm(p0.x, p1.x, p2.x, p3.x, u, u2, u3),
                cm(p0.y, p1.y, p2.y, p3.y, u, u2, u3),
                cm(p0.z, p1.z, p2.z, p3.z, u, u2, u3));
    }

    private static double cm(double a, double b, double c, double d, double u, double u2, double u3) {
        return 0.5 * ((2 * b) + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u2 + (-a + 3 * b - 3 * c + d) * u3);
    }
}
