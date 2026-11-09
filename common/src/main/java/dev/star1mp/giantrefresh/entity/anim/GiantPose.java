package dev.star1mp.giantrefresh.entity.anim;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Procedural animation of the Giant, shared by the client model and by the server/client rider
 * positioning (so riders on the hat follow the head exactly). Everything here is plain math:
 * no client classes, so it is safe on a dedicated server.
 *
 * <p>Units: model pixels of the unscaled model (the whole model is scaled ×2 when rendered),
 * angles in radians. Model space is the usual Minecraft one: +y is down, -z is the front.
 */
public final class GiantPose {
    // ------------------------------------------------------------------ geometry (unscaled pixels)
    public static final float MODEL_SCALE = 2.0F;
    public static final float HIP_Y = -12.0F;
    public static final float SEGMENT = 18.0F;
    public static final float NECK_Y = -36.0F;
    public static final float HEAD_SIZE = 24.0F;
    public static final float SHOULDER_X = 18.0F;
    public static final float SHOULDER_Y = -33.0F;
    /** Top of the harness hat in head space. */
    public static final float HAT_TOP = -24.6F;
    /** Hip height when sitting: the hips rest 6 px above the ground. */
    public static final float SIT_HIP_DROP = 30.0F;

    // ------------------------------------------------------------------ attack ids (mirror GiantEntity)
    public static final int PUNCH = 1;
    public static final int STOMP = 2;
    public static final int SCOOP = 3;

    public static final int GESTURE_NONE = 0;
    public static final int GESTURE_SCRATCH = 1;
    public static final int GESTURE_LOOK = 2;
    public static final int GESTURE_STRETCH = 3;
    public static final int GESTURE_THUMP = 4;

    public static final int ROAR_LENGTH = 40;
    public static final int WAKE_LENGTH = 50;

    public static int gestureLength(int gesture) {
        return switch (gesture) {
            case GESTURE_SCRATCH -> 70;
            case GESTURE_LOOK -> 80;
            case GESTURE_STRETCH -> 60;
            case GESTURE_THUMP -> 56;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ inputs
    public float age;
    public float walkPos;
    public float walkSpeed;
    /** Head yaw relative to the body and head pitch, degrees. */
    public float headYaw;
    public float headPitch;
    public int attack;
    public float attackTime;
    public float sleep;
    public float aggro;
    public float seed;
    /** 1 right after being hit, fading to 0. */
    public float hurt;
    /** Ticks since the roar started, or -1. */
    public float roar = -1;
    /** Ticks since waking up, or -1. */
    public float wake = -1;
    public int gesture;
    public float gestureTime;
    public float death;

    // ------------------------------------------------------------------ outputs
    public float hipDrop;
    public float bodyRx, bodyRy, bodyRz;
    public float headRx, headRy, headRz;
    public final Limb rightArm = new Limb();
    public final Limb leftArm = new Limb();
    public final Limb rightLeg = new Limb();
    public final Limb leftLeg = new Limb();
    public boolean eyesClosed;

    public static final class Limb {
        public float rx, ry, rz, lower;

        void set(float rx, float ry, float rz, float lower) {
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.lower = lower;
        }
    }

    // ================================================================== animation
    public void compute() {
        float s = Mth.clamp(walkSpeed * 1.3F, 0.0F, 1.0F);
        float ph = walkPos * 0.6662F;
        float breath = Mth.sin(age * 0.055F + seed);

        // ---- heavy, rolling walk
        float thigh = Mth.cos(ph) * 0.55F * s;
        rightLeg.set(thigh, 0.04F, 0.03F, Math.max(0.0F, Mth.sin(ph)) * 1.1F * s);
        leftLeg.set(-thigh, -0.04F, -0.03F, Math.max(0.0F, -Mth.sin(ph)) * 1.1F * s);

        bodyRx = 0.06F + 0.06F * s + aggro * 0.14F + breath * 0.012F;
        bodyRz = Mth.sin(ph) * 0.05F * s + Mth.sin(age * 0.031F + seed * 2.0F) * 0.012F;
        bodyRy = Mth.cos(ph) * 0.09F * s;

        float yaw = Mth.clamp(headYaw * Mth.DEG_TO_RAD, -0.8F, 0.8F);
        headRy = yaw - bodyRy * 0.8F;
        headRx = Mth.clamp(headPitch * Mth.DEG_TO_RAD, -0.5F, 0.5F) * 0.7F - bodyRx * 0.7F
                + Mth.cos(ph * 2.0F) * 0.04F * s + breath * 0.012F;
        headRz = -bodyRz * 0.6F + Mth.sin(age * 0.023F + seed) * 0.015F;

        // heavy arms swing a little behind the legs (follow-through)
        float swing = -Mth.cos(ph - 0.45F) * 0.45F * s;
        float idleSway = Mth.sin(age * 0.04F + seed * 3.0F) * 0.03F;
        rightArm.set(-0.08F + breath * 0.03F + swing + idleSway, 0.0F, 0.11F + breath * 0.015F,
                -0.18F - Math.max(0.0F, -swing) * 0.6F);
        leftArm.set(-0.08F - breath * 0.02F - swing - idleSway, 0.0F, -0.11F - breath * 0.015F,
                -0.18F - Math.max(0.0F, swing) * 0.6F);

        // ---- hunting: arms reach forward like a zombie, swaying
        if (aggro > 0.0F) {
            float a = aggro;
            float reach = Mth.sin(age * 0.12F + seed) * 0.07F;
            rightArm.rx = Mth.lerp(a, rightArm.rx, -1.25F + reach + swing * 0.25F);
            leftArm.rx = Mth.lerp(a, leftArm.rx, -1.25F - reach - swing * 0.25F);
            rightArm.ry = Mth.lerp(a, rightArm.ry, -0.1F);
            leftArm.ry = Mth.lerp(a, leftArm.ry, 0.1F);
            rightArm.lower = Mth.lerp(a, rightArm.lower, -0.3F);
            leftArm.lower = Mth.lerp(a, leftArm.lower, -0.3F);
        }

        applyGesture();
        applyWake();
        applyRoar();
        applyAttack();
        applyHurt();

        hipDrop = SEGMENT * 2.0F - Math.max(legExtent(rightLeg), legExtent(leftLeg));

        applySleep(breath);
        applyDeath();

        eyesClosed = sleep > 0.5F || isBlinking();
    }

    private boolean isBlinking() {
        int t = (int) (age + seed * 37.0F);
        return Math.floorMod(t, 97) < 3 || (roar >= 0 && roar < ROAR_LENGTH && roar > 6 && roar < 9);
    }

    private static float legExtent(Limb leg) {
        return SEGMENT * Mth.cos(leg.rx) + SEGMENT * Mth.cos(leg.rx + leg.lower);
    }

    // ------------------------------------------------------------------ keyframe helper
    private static final float B = Float.NaN;

    /**
     * Smooth keyframe track. {@code kv} holds (time, value) pairs; a NaN value means "the base pose".
     */
    private static float track(float t, float base, float... kv) {
        if (t <= kv[0]) {
            return v(kv[1], base);
        }
        for (int i = 2; i < kv.length; i += 2) {
            if (t <= kv[i]) {
                float t0 = kv[i - 2];
                float k = (t - t0) / Math.max(1.0E-4F, kv[i] - t0);
                k = k * k * (3.0F - 2.0F * k);
                return Mth.lerp(k, v(kv[i - 1], base), v(kv[i + 1], base));
            }
        }
        return v(kv[kv.length - 1], base);
    }

    private static float v(float value, float base) {
        return Float.isNaN(value) ? base : value;
    }

    // ------------------------------------------------------------------ attacks
    private void applyAttack() {
        float t = attackTime;
        switch (attack) {
            case PUNCH -> {
                float armRx = track(t, rightArm.rx, 0, B, 20, -2.75F, 23, -0.7F, 28, -0.7F, 34, B);
                float armRxL = track(t, leftArm.rx, 0, B, 20, -2.75F, 23, -0.7F, 28, -0.7F, 34, B);
                float tremble = (t > 13 && t < 20) ? Mth.sin(t * 3.1F) * 0.06F : 0.0F;
                rightArm.rx = armRx + tremble;
                leftArm.rx = armRxL - tremble;
                rightArm.lower = track(t, rightArm.lower, 0, B, 20, -1.35F, 23, 0.0F, 28, -0.1F, 34, B);
                leftArm.lower = track(t, leftArm.lower, 0, B, 20, -1.35F, 23, 0.0F, 28, -0.1F, 34, B);
                rightArm.rz = track(t, rightArm.rz, 0, B, 20, 0.28F, 23, 0.1F, 34, B);
                leftArm.rz = track(t, leftArm.rz, 0, B, 20, -0.28F, 23, -0.1F, 34, B);
                rightArm.ry = track(t, rightArm.ry, 0, B, 20, 0.0F, 34, B);
                leftArm.ry = track(t, leftArm.ry, 0, B, 20, 0.0F, 34, B);
                bodyRx = track(t, bodyRx, 0, B, 20, -0.2F, 23, 0.45F, 28, 0.4F, 34, B);
                headRx = track(t, headRx, 0, B, 20, -0.3F, 23, 0.3F, 34, B);
                rightLeg.rx = track(t, rightLeg.rx, 0, B, 18, -0.15F, 23, -0.3F, 34, B);
                leftLeg.rx = track(t, leftLeg.rx, 0, B, 18, -0.15F, 23, -0.3F, 34, B);
                rightLeg.lower = track(t, rightLeg.lower, 0, B, 18, 0.3F, 23, 0.55F, 34, B);
                leftLeg.lower = track(t, leftLeg.lower, 0, B, 18, 0.3F, 23, 0.55F, 34, B);
            }
            case STOMP -> {
                rightLeg.rx = track(t, rightLeg.rx, 0, B, 22, -1.25F, 26, 0.05F, 30, 0.05F, 40, B);
                rightLeg.lower = track(t, rightLeg.lower, 0, B, 22, 1.45F, 26, 0.05F, 40, B);
                leftLeg.rx = track(t, leftLeg.rx, 0, B, 22, -0.1F, 26, -0.15F, 40, B);
                leftLeg.lower = track(t, leftLeg.lower, 0, B, 22, 0.25F, 26, 0.35F, 32, 0.1F, 40, B);
                bodyRx = track(t, bodyRx, 0, B, 22, -0.18F, 26, 0.28F, 32, 0.2F, 40, B);
                bodyRz = track(t, bodyRz, 0, B, 22, -0.12F, 26, 0.02F, 40, B);
                rightArm.rz = track(t, rightArm.rz, 0, B, 22, 0.95F, 26, 0.5F, 40, B);
                leftArm.rz = track(t, leftArm.rz, 0, B, 22, -0.95F, 26, -0.5F, 40, B);
                rightArm.rx = track(t, rightArm.rx, 0, B, 22, -0.5F, 26, -0.2F, 40, B);
                leftArm.rx = track(t, leftArm.rx, 0, B, 22, -0.5F, 26, -0.2F, 40, B);
                rightArm.lower = track(t, rightArm.lower, 0, B, 22, -0.6F, 40, B);
                leftArm.lower = track(t, leftArm.lower, 0, B, 22, -0.6F, 40, B);
                headRx = track(t, headRx, 0, B, 22, -0.2F, 26, 0.35F, 40, B);
            }
            case SCOOP -> {
                rightLeg.rx = track(t, rightLeg.rx, 0, B, 12, -0.55F, 16, -0.2F, 28, B);
                leftLeg.rx = track(t, leftLeg.rx, 0, B, 12, -0.55F, 16, -0.2F, 28, B);
                rightLeg.lower = track(t, rightLeg.lower, 0, B, 12, 1.0F, 16, 0.3F, 28, B);
                leftLeg.lower = track(t, leftLeg.lower, 0, B, 12, 1.0F, 16, 0.3F, 28, B);
                bodyRx = track(t, bodyRx, 0, B, 12, 0.55F, 16, 0.15F, 28, B);
                bodyRy = track(t, bodyRy, 0, B, 12, 0.35F, 16, -0.45F, 28, B);
                rightArm.rx = track(t, rightArm.rx, 0, B, 12, 0.7F, 15, -1.0F, 18, -2.5F, 28, B);
                rightArm.lower = track(t, rightArm.lower, 0, B, 12, -0.3F, 18, -0.7F, 28, B);
                rightArm.rz = track(t, rightArm.rz, 0, B, 12, 0.35F, 18, 0.15F, 28, B);
                leftArm.rx = track(t, leftArm.rx, 0, B, 12, -0.6F, 28, B);
                leftArm.rz = track(t, leftArm.rz, 0, B, 12, -0.35F, 28, B);
                headRx = track(t, headRx, 0, B, 12, 0.2F, 18, -0.35F, 28, B);
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ roar / wake / gestures / hurt
    private void applyRoar() {
        if (roar < 0 || roar > ROAR_LENGTH) {
            return;
        }
        float t = roar;
        bodyRx = track(t, bodyRx, 0, B, 8, -0.28F, 30, -0.28F, 40, B);
        rightArm.rx = track(t, rightArm.rx, 0, B, 8, -0.9F, 30, -0.95F, 40, B);
        leftArm.rx = track(t, leftArm.rx, 0, B, 8, -0.9F, 30, -0.95F, 40, B);
        rightArm.rz = track(t, rightArm.rz, 0, B, 8, 1.15F, 30, 1.2F, 40, B);
        leftArm.rz = track(t, leftArm.rz, 0, B, 8, -1.15F, 30, -1.2F, 40, B);
        rightArm.lower = track(t, rightArm.lower, 0, B, 8, -0.9F, 30, -1.0F, 40, B);
        leftArm.lower = track(t, leftArm.lower, 0, B, 8, -0.9F, 30, -1.0F, 40, B);
        headRx = track(t, headRx, 0, B, 8, -0.65F, 30, -0.6F, 40, B);
        if (t > 8 && t < 30) {
            float shake = Mth.sin(t * 1.7F) * 0.07F;
            headRy += shake;
            bodyRz += shake * 0.3F;
        }
    }

    private void applyWake() {
        if (wake < 0 || wake > WAKE_LENGTH) {
            return;
        }
        float t = wake;
        rightArm.rx = track(t, rightArm.rx, 0, B, 14, B, 26, -2.9F, 38, -2.9F, 50, B);
        leftArm.rx = track(t, leftArm.rx, 0, B, 14, B, 26, -2.9F, 38, -2.9F, 50, B);
        rightArm.rz = track(t, rightArm.rz, 0, B, 14, B, 26, 0.35F, 38, 0.35F, 50, B);
        leftArm.rz = track(t, leftArm.rz, 0, B, 14, B, 26, -0.35F, 38, -0.35F, 50, B);
        rightArm.lower = track(t, rightArm.lower, 0, B, 14, B, 26, -0.2F, 50, B);
        leftArm.lower = track(t, leftArm.lower, 0, B, 14, B, 26, -0.2F, 50, B);
        bodyRx = track(t, bodyRx, 0, B, 14, B, 26, -0.25F, 38, -0.22F, 50, B);
        headRx = track(t, headRx, 0, B, 14, B, 26, -0.55F, 38, -0.5F, 50, B);
    }

    private void applyGesture() {
        if (gesture == GESTURE_NONE) {
            return;
        }
        float t = gestureTime;
        switch (gesture) {
            case GESTURE_SCRATCH -> {
                float scratch = (t > 12 && t < 58) ? Mth.sin(t * 1.3F) * 0.22F : 0.0F;
                rightArm.rx = track(t, rightArm.rx, 0, B, 12, -2.6F, 58, -2.6F, 70, B);
                rightArm.rz = track(t, rightArm.rz, 0, B, 12, -0.35F, 58, -0.35F, 70, B);
                rightArm.lower = track(t, rightArm.lower, 0, B, 12, -1.7F, 58, -1.7F, 70, B) + scratch;
                headRz = track(t, headRz, 0, B, 12, 0.12F, 58, 0.12F, 70, B);
                headRx = track(t, headRx, 0, B, 12, 0.12F, 58, 0.12F, 70, B);
            }
            case GESTURE_LOOK -> {
                headRy = track(t, headRy, 0, B, 15, 0.85F, 35, 0.85F, 50, -0.85F, 68, -0.85F, 80, B);
                bodyRy = track(t, bodyRy, 0, B, 15, 0.22F, 35, 0.22F, 50, -0.22F, 68, -0.22F, 80, B);
            }
            case GESTURE_STRETCH -> {
                bodyRx = track(t, bodyRx, 0, B, 20, -0.22F, 40, -0.22F, 60, B);
                rightArm.rx = track(t, rightArm.rx, 0, B, 20, 0.55F, 40, 0.55F, 60, B);
                leftArm.rx = track(t, leftArm.rx, 0, B, 20, 0.55F, 40, 0.55F, 60, B);
                rightArm.rz = track(t, rightArm.rz, 0, B, 20, 0.3F, 40, 0.3F, 60, B);
                leftArm.rz = track(t, leftArm.rz, 0, B, 20, -0.3F, 40, -0.3F, 60, B);
                rightArm.lower = track(t, rightArm.lower, 0, B, 20, -0.3F, 60, B);
                leftArm.lower = track(t, leftArm.lower, 0, B, 20, -0.3F, 60, B);
                headRx = track(t, headRx, 0, B, 20, -0.4F, 40, -0.4F, 60, B);
            }
            case GESTURE_THUMP -> {
                float beat = (t > 10 && t < 46) ? Mth.sin(t * 0.9F) * 0.35F : 0.0F;
                rightArm.rx = track(t, rightArm.rx, 0, B, 10, -0.9F, 46, -0.9F, 56, B) + beat;
                leftArm.rx = track(t, leftArm.rx, 0, B, 10, -0.9F, 46, -0.9F, 56, B) - beat;
                rightArm.lower = track(t, rightArm.lower, 0, B, 10, -1.9F, 46, -1.9F, 56, B);
                leftArm.lower = track(t, leftArm.lower, 0, B, 10, -1.9F, 46, -1.9F, 56, B);
                rightArm.rz = track(t, rightArm.rz, 0, B, 10, -0.45F, 46, -0.45F, 56, B);
                leftArm.rz = track(t, leftArm.rz, 0, B, 10, 0.45F, 46, 0.45F, 56, B);
                bodyRx = track(t, bodyRx, 0, B, 10, -0.1F, 46, -0.1F, 56, B);
                headRx = track(t, headRx, 0, B, 10, -0.3F, 46, -0.3F, 56, B);
            }
            default -> {}
        }
    }

    private void applyHurt() {
        if (hurt <= 0.0F) {
            return;
        }
        float h = hurt * hurt;
        bodyRx -= 0.12F * h;
        headRx -= 0.22F * h;
        bodyRz += 0.06F * h * (seed > Mth.PI ? 1.0F : -1.0F);
    }

    // ------------------------------------------------------------------ sleep & death
    private void applySleep(float breath) {
        if (sleep <= 0.0F) {
            return;
        }
        float w = sleep * sleep * (3.0F - 2.0F * sleep);
        float slow = Mth.sin(age * 0.05F + seed);
        rightLeg.rx = Mth.lerp(w, rightLeg.rx, -2.15F);
        leftLeg.rx = Mth.lerp(w, leftLeg.rx, -2.15F);
        rightLeg.ry = Mth.lerp(w, rightLeg.ry, 0.2F);
        leftLeg.ry = Mth.lerp(w, leftLeg.ry, -0.2F);
        rightLeg.lower = Mth.lerp(w, rightLeg.lower, 1.75F);
        leftLeg.lower = Mth.lerp(w, leftLeg.lower, 1.75F);
        bodyRx = Mth.lerp(w, bodyRx, 0.42F + slow * 0.03F);
        bodyRy = Mth.lerp(w, bodyRy, 0.0F);
        bodyRz = Mth.lerp(w, bodyRz, 0.0F);
        // arms folded over the raised knees, forearms resting on top of them
        rightArm.rx = Mth.lerp(w, rightArm.rx, -0.45F + slow * 0.02F);
        leftArm.rx = Mth.lerp(w, leftArm.rx, -0.45F + slow * 0.02F);
        rightArm.ry = Mth.lerp(w, rightArm.ry, 0.0F);
        leftArm.ry = Mth.lerp(w, leftArm.ry, 0.0F);
        rightArm.rz = Mth.lerp(w, rightArm.rz, -0.2F);
        leftArm.rz = Mth.lerp(w, leftArm.rz, 0.2F);
        rightArm.lower = Mth.lerp(w, rightArm.lower, -1.35F);
        leftArm.lower = Mth.lerp(w, leftArm.lower, -1.35F);
        headRx = Mth.lerp(w, headRx, 0.55F + slow * 0.06F);
        headRy = Mth.lerp(w, headRy, 0.0F);
        headRz = Mth.lerp(w, headRz, 0.08F);
        hipDrop = Mth.lerp(w, hipDrop, SIT_HIP_DROP);
    }

    private void applyDeath() {
        if (death <= 0.0F) {
            return;
        }
        float k = Mth.clamp(death / 12.0F, 0.0F, 1.0F);
        rightArm.rx = Mth.lerp(k, rightArm.rx, -2.3F);
        leftArm.rx = Mth.lerp(k, leftArm.rx, -2.1F);
        rightArm.rz = Mth.lerp(k, rightArm.rz, 0.4F);
        leftArm.rz = Mth.lerp(k, leftArm.rz, -0.4F);
        rightArm.lower = Mth.lerp(k, rightArm.lower, -0.2F);
        leftArm.lower = Mth.lerp(k, leftArm.lower, -0.2F);
        headRx = Mth.lerp(k, headRx, -0.5F);
        rightLeg.rx = Mth.lerp(k, rightLeg.rx, 0.0F);
        leftLeg.rx = Mth.lerp(k, leftLeg.rx, 0.0F);
        rightLeg.lower = Mth.lerp(k, rightLeg.lower, 0.0F);
        leftLeg.lower = Mth.lerp(k, leftLeg.lower, 0.0F);
        hipDrop = Mth.lerp(k, hipDrop, 0.0F);
    }

    /** Forward fall angle (degrees) used by the renderer while dying. */
    public static float deathFallDegrees(float deathTime) {
        float t = Mth.clamp(deathTime / 22.0F, 0.0F, 1.0F);
        float fall = t * t * 90.0F;
        if (deathTime > 22.0F) {
            float bounce = Mth.clamp((deathTime - 22.0F) / 6.0F, 0.0F, 1.0F);
            fall -= Mth.sin(bounce * Mth.PI) * 4.0F;
        }
        return fall;
    }

    // ================================================================== forward kinematics
    /**
     * Offset (in blocks, world axes) from the entity position to a point given in head space
     * (unscaled pixels), for the given body yaw. Mirrors exactly what the renderer does.
     */
    public Vector3f headPointOffset(float bodyYawDegrees, float x, float y, float z) {
        Matrix4f m = new Matrix4f()
                .rotateY((180.0F - bodyYawDegrees) * Mth.DEG_TO_RAD)
                .scale(-1.0F, -1.0F, 1.0F)
                .translate(0.0F, -1.501F, 0.0F)
                .translate(0.0F, 24.016F * (1.0F - MODEL_SCALE) / 16.0F, 0.0F)
                .scale(MODEL_SCALE)
                .translate(0.0F, (HIP_Y + hipDrop) / 16.0F, 0.0F)
                .rotateZYX(bodyRz, bodyRy, bodyRx)
                .translate(0.0F, NECK_Y / 16.0F, 0.0F)
                .rotateZYX(headRz, headRy, headRx);
        return m.transformPosition(new Vector3f(x / 16.0F, y / 16.0F, z / 16.0F));
    }
}
