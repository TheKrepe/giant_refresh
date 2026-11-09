package dev.star1mp.giantrefresh.entity.ai;

import dev.star1mp.giantrefresh.entity.GiantEntity;
import java.util.EnumSet;
import dev.star1mp.giantrefresh.config.GRConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Chases the target and picks one of three telegraphed attacks:
 * <ul>
 *     <li>Punch – both fists raised high, then a slam in front.</li>
 *     <li>Stomp – a leg lifted for a long moment, then a shockwave.</li>
 *     <li>Scoop – a low sweep that throws small creatures into the air.</li>
 *     <li>Grab – reaches for the target (down, forward-up or straight up), lifts it overhead and
 *     hurls it at the ground. This is how a Giant deals with players hiding on pillars.</li>
 * </ul>
 */
public class GiantAttackGoal extends Goal {
    private final GiantEntity giant;
    private int repathDelay;

    public GiantAttackGoal(GiantEntity giant) {
        this.giant = giant;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = giant.getTarget();
        return target != null && target.isAlive() && !giant.isGiantSleeping();
    }

    @Override
    public boolean canContinueToUse() {
        return giant.isAttacking() || canUse();
    }

    @Override
    public void start() {
        giant.setAggressive(true);
        repathDelay = 0;
    }

    @Override
    public void stop() {
        giant.setAggressive(false);
        giant.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = giant.getTarget();
        if (giant.isAttacking() || giant.isRoaring()) {
            giant.getNavigation().stop();
            if (target != null) {
                giant.getLookControl().setLookAt(target, 10.0F, 20.0F);
            }
            return;
        }
        if (target == null || !target.isAlive()) {
            return;
        }
        giant.getLookControl().setLookAt(target, 20.0F, 20.0F);

        double dx = target.getX() - giant.getX();
        double dz = target.getZ() - giant.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        if (--repathDelay <= 0) {
            repathDelay = 8 + giant.getRandom().nextInt(6);
            if (horizontal > 3.0) {
                if (target.getY() - giant.getY() > 3.0) {
                    // perched up high: walk to the foot of the perch
                    giant.getNavigation().moveTo(target.getX(), giant.getY(), target.getZ(), 1.0);
                } else {
                    giant.getNavigation().moveTo(target, 1.0);
                }
            } else {
                giant.getNavigation().stop();
            }
        }

        if (giant.getAttackCooldown() > 0) {
            return;
        }
        double dy = target.getY() - giant.getY();
        boolean grabbable = GRConfig.get().giantGrabEnabled && target.getBbHeight() < 3.2F && target.getBbWidth() < 2.0F
                && !target.isPassenger() && giant.hasLineOfSight(target);
        int attack = GiantEntity.ATTACK_NONE;
        if (dy > 3.0) {
            // out of reach of fists and feet: pluck it off its perch
            if (grabbable && dy < 15.5 && horizontal < 5.5) {
                attack = dy < 6.0 ? GiantEntity.ATTACK_GRAB_LOW : dy < 12.0 ? GiantEntity.ATTACK_GRAB : GiantEntity.ATTACK_GRAB_HIGH;
                giant.getNavigation().stop();
                giant.startAttack(attack);
            }
            return;
        }
        float roll = giant.getRandom().nextFloat();
        if (grabbable && target instanceof Player && horizontal < 4.6 && roll < 0.15F) {
            attack = GiantEntity.ATTACK_GRAB_LOW;
        } else if (horizontal < 4.8 && roll < 0.40F) {
            attack = GiantEntity.ATTACK_STOMP;
        } else if (horizontal < 5.6 && target.getBbHeight() < 2.6F && roll < 0.65F) {
            attack = GiantEntity.ATTACK_SCOOP;
        } else if (horizontal < 6.4) {
            attack = GiantEntity.ATTACK_PUNCH;
        }
        if (attack != GiantEntity.ATTACK_NONE) {
            giant.getNavigation().stop();
            giant.startAttack(attack);
        }
    }
}
