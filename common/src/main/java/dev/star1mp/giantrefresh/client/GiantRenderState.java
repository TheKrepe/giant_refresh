package dev.star1mp.giantrefresh.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public class GiantRenderState extends HumanoidRenderState {
    public int attack;
    public float attackTime;
    public float sleep;
    public int hatColor = -1;
    public boolean ridden;
    public boolean aggressive;
}
