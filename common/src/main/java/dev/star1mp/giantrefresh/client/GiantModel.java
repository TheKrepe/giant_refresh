package dev.star1mp.giantrefresh.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.star1mp.giantrefresh.entity.anim.GiantPose;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The Giant: the vanilla zombie body (same boxes and UV layout as a 64×64 skin, with separate
 * left limbs), scaled ×6 like the vanilla Giant. The texture file is 128×128, i.e. twice the
 * vanilla pixel density on the same layout. The body pivots at the hips so it can lean into
 * attacks; two flat eyelids close the eyes while it sleeps. All motion comes from {@link GiantPose}.
 */
public class GiantModel extends EntityModel<GiantRenderState> {
    private final ModelPart body;
    public final ModelPart head;
    private final ModelPart rightEyelid;
    private final ModelPart leftEyelid;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public GiantModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.rightEyelid = head.getChild("right_eyelid");
        this.leftEyelid = head.getChild("left_eyelid");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, -12.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                PartPose.offset(0.0F, GiantPose.HIP_Y, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.offset(0.0F, GiantPose.NECK_Y, 0.0F));
        // eyelids over the painted eyes (face row 4, columns 1-2 and 5-6)
        head.addOrReplaceChild("right_eyelid",
                CubeListBuilder.create().texOffs(56, 16).addBox(-1.0F, -0.5F, 0.0F, 2.0F, 1.0F, 0.0F),
                PartPose.offset(-2.0F, -3.5F, -4.02F));
        head.addOrReplaceChild("left_eyelid",
                CubeListBuilder.create().texOffs(56, 18).addBox(-1.0F, -0.5F, 0.0F, 2.0F, 1.0F, 0.0F),
                PartPose.offset(2.0F, -3.5F, -4.02F));

        body.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-GiantPose.SHOULDER_X, GiantPose.SHOULDER_Y, 0.0F));
        body.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(GiantPose.SHOULDER_X, GiantPose.SHOULDER_Y, 0.0F));
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-GiantPose.HIP_X, GiantPose.HIP_Y, 0.0F));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(GiantPose.HIP_X, GiantPose.HIP_Y, 0.0F));
        return LayerDefinition.create(mesh, 64, 64).apply(MeshTransformer.scaling(GiantPose.MODEL_SCALE));
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

        apply(rightArm, p.rightArm);
        apply(leftArm, p.leftArm);

        rightLeg.y = GiantPose.HIP_Y + p.hipDrop;
        leftLeg.y = GiantPose.HIP_Y + p.hipDrop;
        apply(rightLeg, p.rightLeg);
        apply(leftLeg, p.leftLeg);
    }

    private static void apply(ModelPart part, GiantPose.Limb limb) {
        part.xRot = limb.rx;
        part.yRot = limb.ry;
        part.zRot = limb.rz;
    }

    /** Moves the pose stack into head space (used by the harness hat layer). */
    public void translateToHead(PoseStack poseStack) {
        root().translateAndRotate(poseStack);
        body.translateAndRotate(poseStack);
        head.translateAndRotate(poseStack);
    }
}
