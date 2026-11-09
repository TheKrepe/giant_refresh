package dev.star1mp.giantrefresh.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Happy Ghast harness geometry (same 16 vanilla harness textures) worn as a hat on the Giant's head.
 * Built in harness units (16 px cube) and drawn ×1.5 so it wraps the 24 px head.
 *
 * <p>Goggles: pushed up onto the forehead while nobody rides; pulled down over the eyes when the hat
 * carries a crew. The Giant's skin has its eyes painted exactly under the lenses.
 */
public class GiantHarnessModel extends EntityModel<GiantRenderState> {
    /** Goggle pivot (harness units) when worn over the eyes: lenses cover face rows 12-16 of 24. */
    public static final float GOGGLES_DOWN_Y = -6.33F;
    public static final float GOGGLES_UP_Y = -10.6F;

    private final ModelPart goggles;

    public GiantHarnessModel(ModelPart root) {
        super(root);
        this.goggles = root.getChild("goggles");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("harness",
                CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.4F)),
                PartPose.ZERO);
        root.addOrReplaceChild("goggles",
                CubeListBuilder.create().texOffs(0, 32).addBox(-8.0F, -2.5F, -2.5F, 16.0F, 5.0F, 5.0F, new CubeDeformation(0.15F)),
                PartPose.offset(0.0F, GOGGLES_DOWN_Y, -5.8F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(GiantRenderState state) {
        super.setupAnim(state);
        if (state.ridden) {
            goggles.xRot = 0.0F;
            goggles.y = GOGGLES_DOWN_Y;
            goggles.z = -5.8F;
        } else {
            // pushed up onto the forehead, resting on the brim of the hat
            goggles.xRot = -0.12F;
            goggles.y = GOGGLES_UP_Y;
            goggles.z = -5.9F;
        }
    }
}
