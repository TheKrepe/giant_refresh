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
 * Same geometry as the Happy Ghast harness (and it uses the same 16 vanilla harness textures),
 * shrunk onto the Giant's head as a hat. Goggles are up when nobody rides, and pulled down
 * over the eyes when the hat carries a crew — exactly like on a ridden Happy Ghast.
 */
public class GiantHarnessModel extends EntityModel<GiantRenderState> {
    private final ModelPart goggles;

    public GiantHarnessModel(ModelPart root) {
        super(root);
        this.goggles = root.getChild("goggles");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("harness",
                CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 16.0F, 16.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        root.addOrReplaceChild("goggles",
                CubeListBuilder.create().texOffs(0, 32).addBox(-8.0F, -2.5F, -2.5F, 16.0F, 5.0F, 5.0F, new CubeDeformation(0.15F)),
                PartPose.offset(0.0F, 14.0F, -5.5F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(GiantRenderState state) {
        super.setupAnim(state);
        if (state.ridden) {
            goggles.xRot = 0.0F;
            goggles.y = 14.0F;
        } else {
            goggles.xRot = -0.7854F;
            goggles.y = 9.0F;
        }
    }
}
