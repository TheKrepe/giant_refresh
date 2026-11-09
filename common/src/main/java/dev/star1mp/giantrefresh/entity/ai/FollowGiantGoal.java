package dev.star1mp.giantrefresh.entity.ai;

import dev.star1mp.giantrefresh.entity.GiantEntity;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Lets ordinary zombies gather around a Giant into a small horde, and skeletons keep
 * close to it so it shields them from melee attackers.
 */
public class FollowGiantGoal extends Goal {
    private final PathfinderMob mob;
    private final double minDistance;
    private final double maxDistance;
    private GiantEntity giant;
    private int searchDelay;
    private int repathDelay;

    public FollowGiantGoal(PathfinderMob mob, double minDistance, double maxDistance) {
        this.mob = mob;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.isPassenger() || mob.getTarget() != null) {
            return false;
        }
        if (--searchDelay > 0) {
            return false;
        }
        searchDelay = 40 + mob.getRandom().nextInt(40);
        List<GiantEntity> giants = mob.level().getEntitiesOfClass(GiantEntity.class, mob.getBoundingBox().inflate(28.0),
                g -> g.isAlive() && !g.isGiantSleeping());
        GiantEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (GiantEntity g : giants) {
            double d = mob.distanceToSqr(g);
            if (d < best) {
                best = d;
                nearest = g;
            }
        }
        if (nearest == null || best < maxDistance * maxDistance) {
            return false;
        }
        giant = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return giant != null && giant.isAlive() && mob.getTarget() == null
                && mob.distanceToSqr(giant) > minDistance * minDistance
                && mob.distanceToSqr(giant) < 40.0 * 40.0;
    }

    @Override
    public void start() {
        repathDelay = 0;
    }

    @Override
    public void stop() {
        giant = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (giant == null) {
            return;
        }
        mob.getLookControl().setLookAt(giant, 10.0F, mob.getMaxHeadXRot());
        if (--repathDelay <= 0) {
            repathDelay = 15;
            mob.getNavigation().moveTo(giant, 1.0);
        }
    }
}
