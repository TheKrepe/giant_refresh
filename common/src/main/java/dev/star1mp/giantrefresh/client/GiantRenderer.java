package dev.star1mp.giantrefresh.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.star1mp.giantrefresh.GiantRefresh;
import dev.star1mp.giantrefresh.entity.GiantEntity;
import dev.star1mp.giantrefresh.entity.anim.GiantPose;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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
        giant.fillPose(state.pose, partialTick);
        if (GiantClientHooks.CLIENT_TEST && giant.hasCustomName()) {
            applyTestPose(state, giant.getCustomName().getString());
        }
        state.pose.compute();
        state.hatColor = giant.getHatColor();
        state.ridden = giant.isVehicle();
    }

    /** CI render test only: freeze a showcase pose given as "pose:kind:time" in the custom name. */
    private static void applyTestPose(GiantRenderState state, String name) {
        String[] parts = name.split(":");
        if (parts.length != 3 || !parts[0].equals("pose")) {
            return;
        }
        float t = Float.parseFloat(parts[2]);
        GiantPose p = state.pose;
        switch (parts[1]) {
            case "walk" -> {
                p.walkPos = t;
                p.walkSpeed = 0.8F;
            }
            case "punch" -> {
                p.attack = GiantPose.PUNCH;
                p.attackTime = t;
            }
            case "stomp" -> {
                p.attack = GiantPose.STOMP;
                p.attackTime = t;
            }
            case "scoop" -> {
                p.attack = GiantPose.SCOOP;
                p.attackTime = t;
            }
            case "roar" -> p.roar = t;
            case "death" -> {
                p.death = t;
                state.deathTime = t;
            }
            default -> {
                if (parts[1].startsWith("gesture")) {
                    p.gesture = Integer.parseInt(parts[1].substring(7));
                    p.gestureTime = t;
                }
            }
        }
    }

    @Override
    protected boolean shouldShowName(GiantEntity entity, double distanceSq) {
        if (GiantClientHooks.CLIENT_TEST && entity.hasCustomName() && entity.getCustomName().getString().startsWith("pose:")) {
            return false;
        }
        return super.shouldShowName(entity, distanceSq);
    }

    @Override
    public Identifier getTextureLocation(GiantRenderState state) {
        return TEXTURE;
    }

    /** Instead of the vanilla sideways flop, a dying Giant topples forward like a felled tree. */
    @Override
    protected void setupRotations(GiantRenderState state, PoseStack poseStack, float bodyRot, float scale) {
        if (state.deathTime > 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRot));
            poseStack.mulPose(Axis.XP.rotationDegrees(-GiantPose.deathFallDegrees(state.deathTime)));
            return;
        }
        super.setupRotations(state, poseStack, bodyRot, scale);
    }
}
