package dev.star1mp.giantrefresh.cinema;

import dev.star1mp.giantrefresh.mixin.MobAccessor;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/** A scripted performer: walks to marks, turns, looks at things. Server side. */
public final class Actor {
    public final Entity e;
    private Vec3 mark;
    private double speed;
    private Supplier<Vec3> look;
    private Float faceYaw;
    private boolean run;

    Actor(Entity e) {
        this.e = e;
        if (e instanceof Mob mob) {
            ((MobAccessor) mob).giantrefresh$goalSelector().removeAllGoals(g -> true);
            ((MobAccessor) mob).giantrefresh$targetSelector().removeAllGoals(g -> true);
            mob.setPersistenceRequired();
        }
    }

    public LivingEntity living() { return (LivingEntity) e; }

    /** Walk (Mobs: with their own move control; mannequins: forward input) to a point. */
    public Actor walkTo(Vec3 p, double speed) { mark = p; this.speed = speed; return this; }
    public Actor stop() { mark = null; if (e instanceof LivingEntity l) l.zza = 0; return this; }
    public Actor lookAt(Vec3 p) { look = () -> p; return this; }
    public Actor lookAt(Entity other, double dy) { look = () -> other.position().add(0, dy, 0); return this; }
    public Actor lookAt(Supplier<Vec3> p) { look = p; return this; }
    public Actor face(float yaw) { faceYaw = yaw; look = null; return this; }
    public Actor sprint(boolean on) { run = on; e.setSprinting(on); return this; }
    public Actor crouch(boolean on) {
        e.setShiftKeyDown(on);
        e.setPose(on ? Pose.CROUCHING : Pose.STANDING);
        return this;
    }
    public boolean arrived() { return mark == null; }

    void tick(ServerLevel level) {
        if (!e.isAlive()) {
            return;
        }
        if (mark != null) {
            Vec3 d = mark.subtract(e.position());
            double dist = Math.sqrt(d.x * d.x + d.z * d.z);
            if (dist < 0.35) {
                stop();
            } else if (e instanceof Mob mob) {
                mob.getMoveControl().setWantedPosition(mark.x, mark.y, mark.z, speed);
            } else if (e instanceof LivingEntity l) {
                float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
                l.setYRot(yaw);
                l.yBodyRot = yaw;
                if (look == null) {
                    l.yHeadRot = yaw;
                }
                l.zza = (float) Math.min(1.0, speed);
                l.setSprinting(run);
            }
        }
        if (faceYaw != null && mark == null && e instanceof LivingEntity l) {
            l.setYRot(faceYaw);
            l.yBodyRot = faceYaw;
            l.yHeadRot = faceYaw;
        }
        if (look != null && e instanceof LivingEntity l) {
            Vec3 eye = l.getEyePosition();
            Vec3 d = look.get().subtract(eye);
            float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
            float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
            l.yHeadRot = yaw;
            l.setXRot(pitch);
            if (mark == null) {
                // turn the body along, like a person does when they look over their shoulder
                float diff = Mth.wrapDegrees(yaw - l.yBodyRot);
                if (Math.abs(diff) > 50) {
                    l.yBodyRot += diff - Math.signum(diff) * 50;
                    l.setYRot(l.yBodyRot);
                }
            }
            if (l instanceof Mob mob) {
                mob.getLookControl().setLookAt(look.get());
            }
        }
    }
}
