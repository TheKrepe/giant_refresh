package dev.star1mp.giantrefresh.entity.ai;

import dev.star1mp.giantrefresh.config.GRConfig;
import dev.star1mp.giantrefresh.entity.GiantEntity;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.Vec3;

/**
 * Spiders treat a Giant like a wall: they run up to it, climb straight up its body and settle on
 * its head, from where they pounce on anything that comes close (see GiantRiders.tickRiders).
 * Hostile spiders only: at night, or when they already have a target.
 */
public class ClimbGiantGoal extends Goal {
    private static final double CLIMB_SPEED = 0.22;
    private static final int MAX_CLIMB_TICKS = 160;

    private final Spider spider;
    private GiantEntity giant;
    private boolean climbing;
    private int ticks;
    private int searchDelay;
    private int repathDelay;
    private boolean forced;

    public ClimbGiantGoal(Spider spider) {
        this.spider = spider;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (forced) {
            forced = false;
            return giant != null;
        }
        if (!GRConfig.get().spidersClimbGiants || spider.isPassenger() || spider.isVehicle()) {
            return false;
        }
        if (--searchDelay > 0) {
            return false;
        }
        searchDelay = 60 + spider.getRandom().nextInt(60);
        boolean hostile = spider.getTarget() != null || spider.level().isDarkOutside();
        if (!hostile || spider.getRandom().nextInt(3) != 0) {
            return false;
        }
        List<GiantEntity> giants = spider.level().getEntitiesOfClass(GiantEntity.class, spider.getBoundingBox().inflate(14.0),
                g -> g.isAlive() && !g.isGiantSleeping() && g.hasRoomForClimber());
        giant = giants.isEmpty() ? null : giants.get(spider.getRandom().nextInt(giants.size()));
        return giant != null;
    }

    /** Forced start (used by the smoke test). */
    public void forceClimb(GiantEntity target) {
        this.giant = target;
        this.forced = true;
    }

    @Override
    public boolean canContinueToUse() {
        return giant != null && giant.isAlive() && !spider.isPassenger() && ticks < 400
                && (climbing || spider.distanceToSqr(giant) < 24.0 * 24.0);
    }

    @Override
    public void start() {
        climbing = false;
        ticks = 0;
        repathDelay = 0;
    }

    @Override
    public void stop() {
        if (climbing) {
            spider.setNoGravity(false);
            spider.resetFallDistance();
        }
        climbing = false;
        giant = null;
        spider.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (giant == null) {
            return;
        }
        ticks++;
        double halfWidth = giant.getBbWidth() / 2.0;
        double reach = halfWidth + spider.getBbWidth() / 2.0 + 0.05;
        double dx = spider.getX() - giant.getX();
        double dz = spider.getZ() - giant.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        if (!climbing) {
            spider.getLookControl().setLookAt(giant, 30.0F, 30.0F);
            if (horizontal <= reach + 1.2) {
                climbing = true;
                ticks = 0;
                spider.getNavigation().stop();
                spider.setNoGravity(true);
            } else if (--repathDelay <= 0) {
                repathDelay = 10;
                spider.getNavigation().moveTo(giant, 1.2);
            }
            return;
        }

        // cling to the side of the Giant that faces the spider and crawl up
        double nx = horizontal > 0.01 ? dx / horizontal : 1.0;
        double nz = horizontal > 0.01 ? dz / horizontal : 0.0;
        double y = Math.max(spider.getY(), giant.getY()) + CLIMB_SPEED;
        spider.setPos(giant.getX() + nx * reach, y, giant.getZ() + nz * reach);
        spider.setDeltaMovement(Vec3.ZERO);
        spider.resetFallDistance();
        spider.setClimbing(true);
        float yaw = (float) (Mth.atan2(-nz, -nx) * Mth.RAD_TO_DEG) - 90.0F;
        spider.setYRot(yaw);
        spider.yBodyRot = yaw;
        spider.yHeadRot = yaw;
        if (ticks % 6 == 0) {
            spider.playSound(SoundEvents.SPIDER_STEP, 0.5F, 1.0F);
        }

        if (y >= giant.getY() + giant.getBbHeight() * 0.72) {
            spider.setNoGravity(false);
            climbing = false;
            if (!spider.startRiding(giant)) {
                // no room on the head: let go and drop back down
                spider.setDeltaMovement(nx * 0.4, 0.1, nz * 0.4);
            }
            giant = null;
        } else if (ticks > MAX_CLIMB_TICKS) {
            giant = null;
        }
    }
}
