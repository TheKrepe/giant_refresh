package dev.star1mp.giantrefresh.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.star1mp.giantrefresh.GiantRefresh;
import dev.star1mp.giantrefresh.entity.GiantEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class GiantRenderer extends MobRenderer<GiantEntity, GiantRenderState, GiantModel> {
    private static final Identifier TEXTURE = GiantRefresh.id("textures/entity/giant/giant.png");

    public GiantRenderer(EntityRendererProvider.Context context) {
        super(context, new GiantModel(context.bakeLayer(GRModelLayers.GIANT)), 3.0F);
        this.addLayer(new GiantHarnessLayer(this, new GiantHarnessModel(context.bakeLayer(GRModelLayers.GIANT_HARNESS))));
    }

    @Override
    public GiantRenderState createRenderState() {
        return new GiantRenderState();
    }

    @Override
    public void extractRenderState(GiantEntity giant, GiantRenderState state, float partialTick) {
        super.extractRenderState(giant, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(giant, state, partialTick, this.itemModelResolver);
        state.walkAnimationPos = state.walkAnimationPos / GiantEntity.STRIDE;
        state.attack = giant.getClientAttack();
        state.attackTime = giant.getClientAttackTime(partialTick);
        state.sleep = giant.getSleepAmount(partialTick);
        state.hatColor = giant.getHatColor();
        state.ridden = giant.isVehicle();
        state.aggressive = giant.isAggressive();
    }

    @Override
    public Identifier getTextureLocation(GiantRenderState state) {
        return TEXTURE;
    }

    @Override
    protected void scale(GiantRenderState state, PoseStack poseStack) {
        if (state.sleep > 0.0F) {
            float s = state.sleep * state.sleep * (3.0F - 2.0F * state.sleep);
            // the pose stack is already flipped (y points down here)
            poseStack.translate(0.0F, (float) (GiantEntity.SIT_DROP * s), 0.0F);
        }
    }
}
