package dev.star1mp.giantrefresh.entity.ai;

import dev.star1mp.giantrefresh.entity.GiantEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Chases the target and picks one of three telegraphed attacks:
 * <ul>
 *     <li>Punch – both fists raised high, then a slam in front.</li>
 *     <li>Stomp – a leg lifted for a long moment, then a shockwave.</li>
 *     <li>Scoop – a low sweep that throws small creatures into the air.</li>
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
            if (horizontal > 3.5) {
                giant.getNavigation().moveTo(target, 1.0);
            } else {
                giant.getNavigation().stop();
            }
        }

        if (giant.getAttackCooldown() > 0 || target.getY() > giant.getY() + 9.0) {
            return;
        }
        float roll = giant.getRandom().nextFloat();
        int attack = GiantEntity.ATTACK_NONE;
        if (horizontal < 4.8 && roll < 0.40F) {
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
