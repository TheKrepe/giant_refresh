package dev.star1mp.giantrefresh.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.star1mp.giantrefresh.entity.anim.GiantPose;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The Giant's own model (256×256 texture, scaled ×2): a heavy humanoid with elbows and knees,
 * a torn shirt hem, ragged sleeves and trouser hems as separate overlay layers, and eyelids
 * that blink. All motion comes from {@link GiantPose}.
 */
public class GiantModel extends EntityModel<GiantRenderState> {
    private final ModelPart body;
    public final ModelPart head;
    private final ModelPart rightEyelid;
    private final ModelPart leftEyelid;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart rightLeg;
    private final ModelPart rightShin;
    private final ModelPart leftLeg;
    private final ModelPart leftShin;

    public GiantModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.rightEyelid = head.getChild("right_eyelid");
        this.leftEyelid = head.getChild("left_eyelid");
        this.rightArm = body.getChild("right_arm");
        this.rightForearm = rightArm.getChild("right_forearm");
        this.leftArm = body.getChild("left_arm");
        this.leftForearm = leftArm.getChild("left_forearm");
        this.rightLeg = root.getChild("right_leg");
        this.rightShin = rightLeg.getChild("right_shin");
        this.leftLeg = root.getChild("left_leg");
        this.leftShin = leftLeg.getChild("left_shin");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float seg = GiantPose.SEGMENT;

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(96, 0).addBox(-12.0F, -36.0F, -6.0F, 24.0F, 36.0F, 12.0F),
                PartPose.offset(0.0F, GiantPose.HIP_Y, 0.0F));
        body.addOrReplaceChild("shirt",
                CubeListBuilder.create().texOffs(168, 0).addBox(-12.0F, -36.0F, -6.0F, 24.0F, 36.0F, 12.0F, new CubeDeformation(0.75F)),
                PartPose.ZERO);

        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -24.0F, -12.0F, 24.0F, 24.0F, 24.0F),
                PartPose.offset(0.0F, GiantPose.NECK_Y, 0.0F));
        head.addOrReplaceChild("right_eyelid",
                CubeListBuilder.create().texOffs(192, 48).addBox(-3.0F, -2.0F, 0.0F, 6.0F, 4.0F, 0.0F),
                PartPose.offset(-5.0F, -11.0F, -12.05F));
        head.addOrReplaceChild("left_eyelid",
                CubeListBuilder.create().texOffs(192, 56).addBox(-3.0F, -2.0F, 0.0F, 6.0F, 4.0F, 0.0F),
                PartPose.offset(5.0F, -11.0F, -12.05F));

        limb(body, "right_arm", "right_forearm", 0, 48, 96, 48, 0, 108, -GiantPose.SHOULDER_X, GiantPose.SHOULDER_Y, -3.0F, true);
        limb(body, "left_arm", "left_forearm", 48, 48, 144, 48, 48, 108, GiantPose.SHOULDER_X, GiantPose.SHOULDER_Y, -3.0F, true);
        limb(root, "right_leg", "right_shin", 0, 78, 96, 78, 96, 108, -6.0F, GiantPose.HIP_Y, 0.0F, false);
        limb(root, "left_leg", "left_shin", 48, 78, 144, 78, 144, 108, 6.0F, GiantPose.HIP_Y, 0.0F, false);
        return LayerDefinition.create(mesh, 256, 256).apply(MeshTransformer.scaling(GiantPose.MODEL_SCALE));
    }

    /**
     * Two-segment limb. Arms get a sleeve overlay on the upper segment, legs a ragged
     * trouser hem overlay on the lower one.
     */
    private static void limb(PartDefinition parent, String upperName, String lowerName,
                             int upperU, int upperV, int lowerU, int lowerV, int overlayU, int overlayV,
                             float x, float y, float top, boolean arm) {
        float seg = GiantPose.SEGMENT;
        PartDefinition upper = parent.addOrReplaceChild(upperName,
                CubeListBuilder.create().texOffs(upperU, upperV).addBox(-6.0F, top, -6.0F, 12.0F, seg, 12.0F),
                PartPose.offset(x, y, 0.0F));
        PartDefinition lower = upper.addOrReplaceChild(lowerName,
                CubeListBuilder.create().texOffs(lowerU, lowerV).addBox(-6.0F, 0.0F, -6.0F, 12.0F, seg, 12.0F),
                PartPose.offset(0.0F, top + seg, 0.0F));
        if (arm) {
            upper.addOrReplaceChild(upperName + "_sleeve",
                    CubeListBuilder.create().texOffs(overlayU, overlayV).addBox(-6.0F, top, -6.0F, 12.0F, seg, 12.0F, new CubeDeformation(0.5F)),
                    PartPose.ZERO);
        } else {
            lower.addOrReplaceChild(lowerName + "_hem",
                    CubeListBuilder.create().texOffs(overlayU, overlayV).addBox(-6.0F, 0.0F, -6.0F, 12.0F, seg, 12.0F, new CubeDeformation(0.5F)),
                    PartPose.ZERO);
        }
    }

    @Override
    public void setupAnim(GiantRenderState state) {
        super.setupAnim(state);
        GiantPose p = state.pose;

        body.y = GiantPose.HIP_Y + p.hipDrop;
        body.xRot = p.bodyRx;
        body.yRot = p.bodyRy;
        body.zRot = p.bodyRz;

        head.xRot = p.headRx;
        head.yRot = p.headRy;
        head.zRot = p.headRz;
        rightEyelid.visible = p.eyesClosed;
        leftEyelid.visible = p.eyesClosed;

        apply(rightArm, rightForearm, p.rightArm);
        apply(leftArm, leftForearm, p.leftArm);

        rightLeg.y = GiantPose.HIP_Y + p.hipDrop;
        leftLeg.y = GiantPose.HIP_Y + p.hipDrop;
        apply(rightLeg, rightShin, p.rightLeg);
        apply(leftLeg, leftShin, p.leftLeg);
    }

    private static void apply(ModelPart upper, ModelPart lower, GiantPose.Limb limb) {
        upper.xRot = limb.rx;
        upper.yRot = limb.ry;
        upper.zRot = limb.rz;
        lower.xRot = limb.lower;
    }

    /** Moves the pose stack into head space (used by the harness hat layer). */
    public void translateToHead(PoseStack poseStack) {
        root().translateAndRotate(poseStack);
        body.translateAndRotate(poseStack);
        head.translateAndRotate(poseStack);
    }
}
