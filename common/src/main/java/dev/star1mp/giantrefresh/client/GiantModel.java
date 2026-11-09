package dev.star1mp.giantrefresh.client;

import dev.star1mp.giantrefresh.entity.GiantEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.util.Mth;

/**
 * The classic humanoid body scaled ×6 (exactly like the vanilla Giant), with slow strides
 * and hand-written, clearly telegraphed attack poses.
 */
public class GiantModel extends HumanoidModel<GiantRenderState> {
    public GiantModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        return LayerDefinition.create(mesh, 64, 64).apply(MeshTransformer.scaling(6.0F));
    }

    @Override
    public void setupAnim(GiantRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;

        // Heavy, slightly forward-hanging arms; stretched forward like a zombie while hunting.
        float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.5F * state.walkAnimationSpeed;
        float breathe = Mth.sin(age * 0.05F) * 0.04F;
        if (state.aggressive) {
            rightArm.xRot = -1.35F + breathe + swing * 0.3F;
            leftArm.xRot = -1.35F - breathe - swing * 0.3F;
        } else {
            rightArm.xRot = -0.18F + swing + breathe;
            leftArm.xRot = -0.18F - swing - breathe;
        }
        rightArm.zRot = 0.08F + Mth.cos(age * 0.07F) * 0.03F;
        leftArm.zRot = -0.08F - Mth.cos(age * 0.07F) * 0.03F;
        rightArm.yRot = 0.0F;
        leftArm.yRot = 0.0F;

        head.xRot = Mth.clamp(head.xRot, -0.26F, 0.26F);

        switch (state.attack) {
            case GiantEntity.ATTACK_PUNCH -> animatePunch(state.attackTime);
            case GiantEntity.ATTACK_STOMP -> animateStomp(state.attackTime);
            case GiantEntity.ATTACK_SCOOP -> animateScoop(state.attackTime);
            default -> {}
        }

        if (state.sleep > 0.0F) {
            animateSleep(smooth(state.sleep), age);
        }
    }

    // ------------------------------------------------------------------ attacks
    private static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /** Both fists rise slowly over the head... then come crashing down. */
    private void animatePunch(float t) {
        int w = GiantEntity.impactTick(GiantEntity.ATTACK_PUNCH);
        int len = GiantEntity.attackLength(GiantEntity.ATTACK_PUNCH);
        float raised = -2.85F;
        float slammed = -0.85F;
        float angle;
        if (t < w) {
            angle = Mth.lerp(smooth(t / w), rightArm.xRot, raised);
        } else if (t < w + 3) {
            angle = Mth.lerp((t - w) / 3.0F, raised, slammed);
        } else {
            angle = Mth.lerp(smooth((t - w - 3) / (len - w - 3)), slammed, -0.18F);
        }
        float spread = t < w ? 0.22F * smooth(t / w) : 0.22F * (1.0F - smooth((t - w) / 6.0F));
        rightArm.xRot = angle;
        leftArm.xRot = angle;
        rightArm.zRot = spread;
        leftArm.zRot = -spread;
        // tremble just before the hit — the cue to dodge
        if (t > w - 6 && t < w) {
            float shake = Mth.sin(t * 3.1F) * 0.05F;
            rightArm.xRot += shake;
            leftArm.xRot -= shake;
        }
    }

    /** One leg lifts high, held for a moment, then slams the ground. */
    private void animateStomp(float t) {
        int w = GiantEntity.impactTick(GiantEntity.ATTACK_STOMP);
        int len = GiantEntity.attackLength(GiantEntity.ATTACK_STOMP);
        float lift;
        if (t < w) {
            lift = -1.25F * smooth(t / (w - 4.0F));
        } else if (t < w + 2) {
            lift = Mth.lerp((t - w) / 2.0F, -1.25F, 0.1F);
        } else {
            lift = Mth.lerp(smooth((t - w - 2) / (len - w - 2)), 0.1F, 0.0F);
        }
        rightLeg.xRot = lift;
        leftLeg.xRot = -lift * 0.12F;
        float balance = t < w ? smooth(t / w) : 1.0F - smooth((t - w) / 8.0F);
        rightArm.zRot = 0.08F + 0.65F * balance;
        leftArm.zRot = -0.08F - 0.65F * balance;
        rightArm.xRot = Mth.lerp(balance, rightArm.xRot, -0.35F);
        leftArm.xRot = Mth.lerp(balance, leftArm.xRot, -0.35F);
    }

    /** A low underarm sweep that flings small things into the air. */
    private void animateScoop(float t) {
        int w = GiantEntity.impactTick(GiantEntity.ATTACK_SCOOP);
        int len = GiantEntity.attackLength(GiantEntity.ATTACK_SCOOP);
        float angle;
        if (t < w) {
            angle = Mth.lerp(smooth(t / w), rightArm.xRot, 0.75F); // swing back
        } else if (t < w + 3) {
            angle = Mth.lerp((t - w) / 3.0F, 0.75F, -2.6F); // sweep up and forward
        } else {
            angle = Mth.lerp(smooth((t - w - 3) / (len - w - 3)), -2.6F, -0.18F);
        }
        rightArm.xRot = angle;
        rightArm.zRot = 0.25F;
        rightArm.yRot = t < w ? -0.35F * smooth(t / w) : -0.35F;
    }

    /** Sitting on the ground, head down, arms resting on the knees. */
    private void animateSleep(float s, float age) {
        float breath = Mth.sin(age * 0.06F) * 0.04F * s;
        rightLeg.xRot = Mth.lerp(s, rightLeg.xRot, -1.41F);
        rightLeg.yRot = Mth.lerp(s, rightLeg.yRot, 0.31F);
        rightLeg.zRot = Mth.lerp(s, rightLeg.zRot, 0.08F);
        leftLeg.xRot = Mth.lerp(s, leftLeg.xRot, -1.41F);
        leftLeg.yRot = Mth.lerp(s, leftLeg.yRot, -0.31F);
        leftLeg.zRot = Mth.lerp(s, leftLeg.zRot, -0.08F);
        rightArm.xRot = Mth.lerp(s, rightArm.xRot, -0.62F) + breath;
        leftArm.xRot = Mth.lerp(s, leftArm.xRot, -0.62F) + breath;
        rightArm.zRot = Mth.lerp(s, rightArm.zRot, 0.12F);
        leftArm.zRot = Mth.lerp(s, leftArm.zRot, -0.12F);
        head.xRot = Mth.lerp(s, head.xRot, 0.5F) + breath;
        head.yRot = Mth.lerp(s, head.yRot, 0.0F);
    }
}
