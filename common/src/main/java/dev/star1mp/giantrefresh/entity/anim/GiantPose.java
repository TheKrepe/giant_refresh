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
    /** Grab-and-throw, reaching down in front, forward-up, or straight up (for targets on pillars). */
    public static final int GRAB_LOW = 4;
    public static final int GRAB = 5;
    public static final int GRAB_HIGH = 6;
    /** Grab attack timeline (ticks). */
    public static final int GRAB_CATCH = 16;
    public static final int GRAB_THROW = 36;
    public static final int GRAB_LENGTH = 52;
    /** Point of the right fist (arm space, unscaled pixels) where a grabbed creature is held. */
    public static final float HAND_X = -1.0F;
    public static final float HAND_Y = 11.0F;

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
            case GRAB_LOW, GRAB, GRAB_HIGH -> applyGrab(t);
            default -> {}
        }
    }

    /**
     * Reach out with the right hand (low / forward-up / straight up), close it on the target,
     * lift it high over the head and hurl it down at the ground.
     */
    private void applyGrab(float t) {
        float reachArm;
        float reachBody;
        float reachHead;
        float reachRz;
        switch (attack) {
            case GRAB_LOW -> {
                reachArm = -0.85F;
                reachBody = 0.72F;
                reachHead = 0.35F;
                reachRz = -0.1F;
            }
            case GRAB_HIGH -> {
                reachArm = -2.75F;
                reachBody = -0.1F;
                reachHead = -0.55F;
                reachRz = 0.15F;
            }
            default -> {
                reachArm = -2.3F;
                reachBody = 0.05F;
                reachHead = -0.35F;
                reachRz = 0.1F;
            }
        }
        float shake = (t > 20 && t < 32) ? Mth.sin(t * 2.3F) * 0.04F : 0.0F;
        rightArm.rx = track(t, rightArm.rx, 0, B, 14, reachArm, 18, reachArm, 30, -3.0F, 33, -3.1F, 39, -0.8F, 44, -0.8F, 52, B) + shake;
        rightArm.ry = track(t, rightArm.ry, 0, B, 14, 0.0F, 44, 0.0F, 52, B);
        rightArm.rz = track(t, rightArm.rz, 0, B, 14, reachRz, 18, reachRz, 30, 0.12F, 39, 0.1F, 52, B);
        leftArm.rx = track(t, leftArm.rx, 0, B, 14, -0.9F, 30, -0.5F, 39, -1.1F, 52, B);
        leftArm.rz = track(t, leftArm.rz, 0, B, 14, -0.35F, 30, -0.3F, 39, -0.2F, 52, B);
        bodyRx = track(t, bodyRx, 0, B, 14, reachBody, 18, reachBody, 30, -0.15F, 33, -0.18F, 39, 0.35F, 44, 0.3F, 52, B);
        bodyRy = track(t, bodyRy, 0, B, 14, -0.15F, 30, 0.1F, 39, -0.25F, 52, B);
        headRx = track(t, headRx, 0, B, 14, reachHead, 18, reachHead, 30, -0.45F, 39, 0.3F, 52, B);
        rightLeg.rx = track(t, rightLeg.rx, 0, B, 14, attack == GRAB_LOW ? -0.35F : 0.0F, 30, 0.1F, 39, -0.3F, 52, B);
        leftLeg.rx = track(t, leftLeg.rx, 0, B, 14, attack == GRAB_LOW ? 0.3F : 0.0F, 30, -0.1F, 39, 0.25F, 52, B);
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
        Matrix4f m = bodyMatrix(bodyYawDegrees)
                .translate(0.0F, NECK_Y / 16.0F, 0.0F)
                .rotateZYX(headRz, headRy, headRx);
        return m.transformPosition(new Vector3f(x / 16.0F, y / 16.0F, z / 16.0F));
    }

    /** Like {@link #headPointOffset} for a point in the right arm's space (e.g. the fist). */
    public Vector3f rightHandOffset(float bodyYawDegrees, float x, float y, float z) {
        Matrix4f m = bodyMatrix(bodyYawDegrees)
                .translate(-SHOULDER_X / 16.0F, SHOULDER_Y / 16.0F, 0.0F)
                .rotateZYX(rightArm.rz, rightArm.ry, rightArm.rx);
        return m.transformPosition(new Vector3f(x / 16.0F, y / 16.0F, z / 16.0F));
    }

    private Matrix4f bodyMatrix(float bodyYawDegrees) {
        return new Matrix4f()
                .rotateY((180.0F - bodyYawDegrees) * Mth.DEG_TO_RAD)
                .scale(-1.0F, -1.0F, 1.0F)
                .translate(0.0F, -1.501F, 0.0F)
                .translate(0.0F, 24.016F * (1.0F - MODEL_SCALE) / 16.0F, 0.0F)
                .scale(MODEL_SCALE)
                .translate(0.0F, (HIP_Y + hipDrop) / 16.0F, 0.0F)
                .rotateZYX(bodyRz, bodyRy, bodyRx);
    }
}
