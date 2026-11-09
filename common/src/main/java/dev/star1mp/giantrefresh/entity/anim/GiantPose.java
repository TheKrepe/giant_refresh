package dev.star1mp.giantrefresh.entity.anim;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Animation of the Giant, shared by the client model and by the server/client rider positioning
 * (so riders on the hat follow the head exactly). Plain math only: safe on a dedicated server.
 *
 * <p>The look is deliberately vanilla: the zombie model's proportions and the zombie's own
 * animation (arms held out, stiff swinging legs, arm bob), plus a few big, readable poses for
 * the telegraphed attacks, the roar, sleeping (sitting) and waking up.
 *
 * <p>Units: pixels of the unscaled model (the model is the zombie scaled ×6 like the vanilla
 * Giant), angles in radians. Model space: +y is down, -z is the front.
 */
public final class GiantPose {
    // ------------------------------------------------------------------ geometry (unscaled pixels)
    public static final float MODEL_SCALE = 6.0F;
    /** Hip pivot (legs and body). */
    public static final float HIP_Y = 12.0F;
    /** Neck, relative to the body pivot. */
    public static final float NECK_Y = -12.0F;
    public static final float SHOULDER_X = 5.0F;
    /** Shoulder pivot, relative to the body pivot. */
    public static final float SHOULDER_Y = -10.0F;
    public static final float HIP_X = 1.9F;
    /** Top of the harness hat in head space (16 px harness ×0.5, inflated 0.4). */
    public static final float HAT_TOP = -8.2F;
    /** How far the hips sink when the Giant sits down: the thighs rest on the ground. */
    public static final float SIT_HIP_DROP = 10.0F;

    // ------------------------------------------------------------------ attack ids (mirror GiantEntity)
    public static final int PUNCH = 1;
    public static final int STOMP = 2;
    public static final int SCOOP = 3;

    public static final int ROAR_LENGTH = 40;
    public static final int WAKE_LENGTH = 50;

    private static final float ZOMBIE_ARMS = -Mth.PI / 2.25F;
    private static final float ZOMBIE_ARMS_AGGRESSIVE = -Mth.PI / 1.5F;

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
    /** Ticks since the roar started, or -1. */
    public float roar = -1;
    /** Ticks since waking up, or -1. */
    public float wake = -1;

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
        public float rx, ry, rz;

        void set(float rx, float ry, float rz) {
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
        }
    }

    // ================================================================== animation
    public void compute() {
        hipDrop = 0.0F;
        bodyRx = bodyRy = bodyRz = 0.0F;

        // ---- vanilla humanoid head look
        headRy = headYaw * Mth.DEG_TO_RAD;
        headRx = headPitch * Mth.DEG_TO_RAD;
        headRz = 0.0F;

        // ---- vanilla humanoid legs: stiff swing
        float ph = walkPos * 0.6662F;
        float swing = Math.min(1.0F, 1.4F * walkSpeed);
        rightLeg.set(Mth.cos(ph) * swing, 0.0F, 0.0F);
        leftLeg.set(Mth.cos(ph + Mth.PI) * swing, 0.0F, 0.0F);

        // ---- vanilla zombie arms: held out in front, raised higher when hunting, with the arm bob
        float arms = Mth.lerp(aggro, ZOMBIE_ARMS, ZOMBIE_ARMS_AGGRESSIVE);
        rightArm.set(arms, -0.1F, 0.0F);
        leftArm.set(arms, 0.1F, 0.0F);
        float bobZ = Mth.cos(age * 0.09F) * 0.05F + 0.05F;
        float bobX = Mth.sin(age * 0.067F) * 0.05F;
        rightArm.rz += bobZ;
        leftArm.rz -= bobZ;
        rightArm.rx += bobX;
        leftArm.rx -= bobX;

        applyWake();
        applyRoar();
        applyAttack();
        applySleep();

        eyesClosed = sleep > 0.5F;
    }

    // ------------------------------------------------------------------ keyframe helper
    private static final float B = Float.NaN;

    /** Smooth keyframe track. {@code kv} holds (time, value) pairs; a NaN value means "the base pose". */
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
                // both fists raised high over the head... and brought down like a hammer
                float tremble = (t > 12 && t < 20) ? Mth.sin(t * 3.1F) * 0.05F : 0.0F;
                rightArm.rx = track(t, rightArm.rx, 0, B, 18, -3.0F, 23, -0.75F, 28, -0.75F, 34, B) + tremble;
                leftArm.rx = track(t, leftArm.rx, 0, B, 18, -3.0F, 23, -0.75F, 28, -0.75F, 34, B) - tremble;
                rightArm.ry = track(t, rightArm.ry, 0, B, 18, 0.0F, 34, B);
                leftArm.ry = track(t, leftArm.ry, 0, B, 18, 0.0F, 34, B);
                rightArm.rz = track(t, rightArm.rz, 0, B, 18, 0.25F, 23, 0.15F, 34, B);
                leftArm.rz = track(t, leftArm.rz, 0, B, 18, -0.25F, 23, -0.15F, 34, B);
                bodyRx = track(t, bodyRx, 0, B, 18, -0.12F, 23, 0.3F, 28, 0.3F, 34, B);
                headRx = track(t, headRx, 0, B, 18, -0.35F, 23, 0.25F, 34, B);
                rightLeg.rx = track(t, rightLeg.rx, 0, B, 18, 0.0F, 23, -0.3F, 28, -0.3F, 34, B);
                leftLeg.rx = track(t, leftLeg.rx, 0, B, 18, 0.0F, 23, 0.25F, 28, 0.25F, 34, B);
            }
            case STOMP -> {
                // one leg lifted high in front, arms out for balance, then slammed down
                rightLeg.rx = track(t, rightLeg.rx, 0, B, 22, -1.25F, 26, 0.0F, 32, 0.0F, 40, B);
                leftLeg.rx = track(t, leftLeg.rx, 0, B, 22, 0.15F, 26, 0.0F, 40, B);
                bodyRx = track(t, bodyRx, 0, B, 22, -0.12F, 26, 0.18F, 32, 0.12F, 40, B);
                bodyRz = track(t, bodyRz, 0, B, 22, 0.06F, 26, 0.0F, 40, B);
                rightArm.rx = track(t, rightArm.rx, 0, B, 22, -0.6F, 26, -0.9F, 40, B);
                leftArm.rx = track(t, leftArm.rx, 0, B, 22, -0.6F, 26, -0.9F, 40, B);
                rightArm.rz = track(t, rightArm.rz, 0, B, 22, 0.75F, 26, 0.35F, 40, B);
                leftArm.rz = track(t, leftArm.rz, 0, B, 22, -0.75F, 26, -0.35F, 40, B);
                headRx = track(t, headRx, 0, B, 22, -0.15F, 26, 0.3F, 40, B);
            }
            case SCOOP -> {
                // a wide underarm sweep: the right arm swings from behind, along the ground, up in front
                rightArm.rx = track(t, rightArm.rx, 0, B, 12, 0.7F, 15, -0.9F, 18, -2.5F, 28, B);
                rightArm.ry = track(t, rightArm.ry, 0, B, 12, 0.0F, 18, 0.2F, 28, B);
                rightArm.rz = track(t, rightArm.rz, 0, B, 12, 0.25F, 18, 0.1F, 28, B);
                bodyRx = track(t, bodyRx, 0, B, 12, 0.4F, 16, 0.15F, 28, B);
                bodyRy = track(t, bodyRy, 0, B, 12, 0.3F, 16, -0.35F, 28, B);
                rightLeg.rx = track(t, rightLeg.rx, 0, B, 12, -0.45F, 16, -0.3F, 28, B);
                leftLeg.rx = track(t, leftLeg.rx, 0, B, 12, 0.3F, 16, 0.2F, 28, B);
                headRx = track(t, headRx, 0, B, 12, 0.2F, 18, -0.3F, 28, B);
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ roar / wake
    private void applyRoar() {
        if (roar < 0 || roar > ROAR_LENGTH) {
            return;
        }
        float t = roar;
        rightArm.rx = track(t, rightArm.rx, 0, B, 8, -2.75F, 30, -2.8F, 40, B);
        leftArm.rx = track(t, leftArm.rx, 0, B, 8, -2.75F, 30, -2.8F, 40, B);
        rightArm.ry = track(t, rightArm.ry, 0, B, 8, 0.0F, 40, B);
        leftArm.ry = track(t, leftArm.ry, 0, B, 8, 0.0F, 40, B);
        rightArm.rz = track(t, rightArm.rz, 0, B, 8, -0.4F, 30, -0.45F, 40, B);
        leftArm.rz = track(t, leftArm.rz, 0, B, 8, 0.4F, 30, 0.45F, 40, B);
        bodyRx = track(t, bodyRx, 0, B, 8, -0.15F, 30, -0.15F, 40, B);
        headRx = track(t, headRx, 0, B, 8, -0.6F, 30, -0.55F, 40, B);
        if (t > 8 && t < 30) {
            float shake = Mth.sin(t * 1.7F) * 0.06F;
            headRy += shake;
            rightArm.rx += shake;
            leftArm.rx -= shake;
        }
    }

    private void applyWake() {
        if (wake < 0 || wake > WAKE_LENGTH) {
            return;
        }
        float t = wake;
        rightArm.rx = track(t, rightArm.rx, 0, B, 14, B, 26, -2.95F, 38, -2.95F, 50, B);
        leftArm.rx = track(t, leftArm.rx, 0, B, 14, B, 26, -2.95F, 38, -2.95F, 50, B);
        rightArm.rz = track(t, rightArm.rz, 0, B, 14, B, 26, -0.3F, 38, -0.3F, 50, B);
        leftArm.rz = track(t, leftArm.rz, 0, B, 14, B, 26, 0.3F, 38, 0.3F, 50, B);
        bodyRx = track(t, bodyRx, 0, B, 14, B, 26, -0.15F, 38, -0.12F, 50, B);
        headRx = track(t, headRx, 0, B, 14, B, 26, -0.5F, 38, -0.45F, 50, B);
    }

    // ------------------------------------------------------------------ sleep: sitting like a riding mob
    private void applySleep() {
        if (sleep <= 0.0F) {
            return;
        }
        float w = sleep * sleep * (3.0F - 2.0F * sleep);
        float breath = Mth.sin(age * 0.05F) * 0.02F;
        // vanilla riding legs
        rightLeg.rx = Mth.lerp(w, rightLeg.rx, -1.62F);
        rightLeg.ry = Mth.lerp(w, rightLeg.ry, Mth.PI / 10.0F);
        rightLeg.rz = Mth.lerp(w, rightLeg.rz, 0.07854F);
        leftLeg.rx = Mth.lerp(w, leftLeg.rx, -1.62F);
        leftLeg.ry = Mth.lerp(w, leftLeg.ry, -Mth.PI / 10.0F);
        leftLeg.rz = Mth.lerp(w, leftLeg.rz, -0.07854F);
        // slumped forward, arms resting on the thighs, head hanging
        bodyRx = Mth.lerp(w, bodyRx, 0.2F + breath);
        rightArm.rx = Mth.lerp(w, rightArm.rx, -0.75F + breath);
        leftArm.rx = Mth.lerp(w, leftArm.rx, -0.75F + breath);
        rightArm.ry = Mth.lerp(w, rightArm.ry, 0.0F);
        leftArm.ry = Mth.lerp(w, leftArm.ry, 0.0F);
        rightArm.rz = Mth.lerp(w, rightArm.rz, 0.05F);
        leftArm.rz = Mth.lerp(w, leftArm.rz, -0.05F);
        headRx = Mth.lerp(w, headRx, 0.45F + breath * 2.0F);
        headRy = Mth.lerp(w, headRy, 0.0F);
        headRz = Mth.lerp(w, headRz, 0.06F);
        hipDrop = Mth.lerp(w, hipDrop, SIT_HIP_DROP);
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
