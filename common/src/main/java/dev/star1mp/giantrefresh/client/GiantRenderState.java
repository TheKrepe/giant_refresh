package dev.star1mp.giantrefresh.client;

import dev.star1mp.giantrefresh.entity.anim.GiantPose;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class GiantRenderState extends LivingEntityRenderState {
    public final GiantPose pose = new GiantPose();
    public int hatColor = -1;
    public boolean ridden;
}
