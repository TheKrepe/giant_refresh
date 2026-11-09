package dev.star1mp.giantrefresh.entity.ai;

import dev.star1mp.giantrefresh.entity.GiantEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * Iron golems see a Giant as the biggest threat around: they spot it from twice their usual
 * range and drop smaller enemies for it. Sleeping Giants are left alone.
 */
public class HuntGiantTargetGoal extends NearestAttackableTargetGoal<GiantEntity> {
    public HuntGiantTargetGoal(IronGolem golem) {
        super(golem, GiantEntity.class, 5, false, false, (target, level) -> target instanceof GiantEntity giant && !giant.isGiantSleeping());
    }

    @Override
    protected double getFollowDistance() {
        return Math.max(32.0, super.getFollowDistance());
    }
}
