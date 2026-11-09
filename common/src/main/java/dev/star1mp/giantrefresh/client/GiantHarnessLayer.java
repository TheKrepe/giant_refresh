package dev.star1mp.giantrefresh.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

/** Renders the harness hat (in one of the 16 vanilla harness colours) on the Giant's head. */
public class GiantHarnessLayer extends RenderLayer<GiantRenderState, GiantModel> {
    private static final Identifier[] TEXTURES = new Identifier[16];

    static {
        for (int i = 0; i < 16; i++) {
            TEXTURES[i] = Identifier.withDefaultNamespace(
                    "textures/entity/equipment/happy_ghast_body/" + DyeColor.byId(i).getSerializedName() + "_harness.png");
        }
    }

    /** Hat size relative to the head (16 → 8.8 units, slightly larger than the 8-unit head). */
    private static final float HAT_SCALE = 0.55F;
    /** Moves the hat so that its top sits just above the top of the head. */
    private static final float HAT_OFFSET = -13.2F / 16.0F;

    private final GiantHarnessModel harness;

    public GiantHarnessLayer(RenderLayerParent<GiantRenderState, GiantModel> parent, GiantHarnessModel harness) {
        super(parent);
        this.harness = harness;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, GiantRenderState state, float yRot, float xRot) {
        if (state.hatColor < 0 || state.hatColor > 15 || state.isInvisible) {
            return;
        }
        GiantModel parent = getParentModel();
        poseStack.pushPose();
        parent.root().translateAndRotate(poseStack);
        parent.head.translateAndRotate(poseStack);
        poseStack.translate(0.0F, HAT_OFFSET, 0.0F);
        poseStack.scale(HAT_SCALE, HAT_SCALE, HAT_SCALE);
        harness.setupAnim(state);
        renderColoredCutoutModel(harness, TEXTURES[state.hatColor], poseStack, collector, light, state, -1, 1);
        poseStack.popPose();
    }
}
