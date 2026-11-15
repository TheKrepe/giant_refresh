package dev.star1mp.giantrefresh.cinema;

import dev.star1mp.giantrefresh.mixin.MobAccessor;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A scripted performer: walks to marks, turns, looks at things. Server side.
 *
 * <p>People (mannequins) and villagers are moved by the script itself, like an actor hitting a mark:
 * they speed up and slow down, turn along an arc instead of snapping, step up and down blocks
 * smoothly and keep a little life in the head (small drifting glances) while they stand.
 * Every performer's position is sent to the client each tick, so the picture never jerks from the
 * usual three-tick network updates.
 */
public final class Actor {
    public final Entity e;
    private Vec3 mark;
    private double speed;
    private Supplier<Vec3> look;
    private Float faceYaw;
    private boolean run;
    private float back;

    /** Moved by the script (villagers and NoAI mobs: their brain would walk them off set). */
    private final boolean kinematic;

    // motion state
    private double v;
    private double heading = Double.NaN;
    private double vy;
    private double headYaw = Double.NaN;
    private double headPitch;
    private double bodyYaw = Double.NaN;
    private int age;
    private final double seed;

    Actor(Entity e) {
        this.e = e;
        this.seed = (e.getId() * 0.6180339887) % 1.0 * 100.0;
        this.kinematic = e instanceof Mob m && (m.isNoAi() || e instanceof net.minecraft.world.entity.npc.villager.AbstractVillager);
        if (e instanceof net.minecraft.world.entity.npc.villager.AbstractVillager vl) {
            vl.setNoAi(true);
        }
        if (e instanceof Mob mob) {
            ((MobAccessor) mob).giantrefresh$goalSelector().removeAllGoals(g -> true);
            ((MobAccessor) mob).giantrefresh$targetSelector().removeAllGoals(g -> true);
            mob.setPersistenceRequired();
        }
    }

    public LivingEntity living() { return (LivingEntity) e; }

    /** Walk to a point at a speed (1.0 = a normal walk, about 4.3 blocks a second). */
    public Actor walkTo(Vec3 p, double speed) { mark = p; this.speed = speed; back = 0; return this; }
    /** Stops at once (for a hit or a throw: physics takes over). */
    public Actor stop() { mark = null; back = 0; v = 0; if (e instanceof LivingEntity l) l.zza = 0; return this; }
    public Actor lookAt(Vec3 p) { look = () -> p; return this; }
    public Actor lookAt(Entity other, double dy) { look = () -> other.position().add(0, dy, 0); return this; }
    public Actor lookAt(Supplier<Vec3> p) { look = p; return this; }
    public Actor face(float yaw) { faceYaw = yaw; look = null; return this; }
    public Actor sprint(boolean on) { run = on; return this; }
    public Actor crouch(boolean on) {
        e.setShiftKeyDown(on);
        e.setPose(on ? Pose.CROUCHING : Pose.STANDING);
        return this;
    }
    /** Kept for old scripts: people always walk on rails now. */
    public Actor rail() { return this; }
    public boolean arrived() { return mark == null && Math.abs(v) < 0.01; }
    /** Step backwards (keeping the current facing). */
    public Actor backUp(float speed) { back = speed; mark = null; return this; }

    private boolean scripted() { return kinematic || !(e instanceof Mob); }

    void tick(ServerLevel level) {
        if (!e.isAlive()) {
            return;
        }
        age++;
        if (e instanceof LivingEntity l && scripted()) {
            move(level, l);
            head(l);
        } else if (e instanceof Mob mob) {
            mobTick(mob);
        }
        if (!e.isPassenger()) {
            ClientboundEntityPositionSyncPacket pk = ClientboundEntityPositionSyncPacket.of(e);
            for (net.minecraft.server.level.ServerPlayer p : level.players()) {
                p.connection.send(pk);
            }
        }
    }

    // ------------------------------------------------------------------ people and villagers

    private void move(ServerLevel level, LivingEntity l) {
        if (Double.isNaN(heading)) {
            heading = l.getYRot();
            bodyYaw = l.yBodyRot;
        }
        double target = 0;
        double desired = heading;
        boolean backing = false;
        if (mark != null) {
            Vec3 d = mark.subtract(e.position());
            double dist = Math.sqrt(d.x * d.x + d.z * d.z);
            if (dist < 0.15 || (dist < 0.45 && v < 0.03)) {
                mark = null;
            } else {
                desired = Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG - 90.0;
                double vmax = speed * (run ? 1.3 : 1.0) * 4.3 / 20.0;
                // ease into the mark: never faster than what still stops on it
                target = Math.min(vmax, Math.sqrt(2 * 0.014 * dist));
            }
        } else if (back != 0) {
            target = -back * 4.3 / 20.0;
            backing = true;
        }
        if (mark != null) {
            double diff = Mth.wrapDegrees(desired - heading);
            double maxTurn = Math.abs(v) < 0.04 ? 16 : (run ? 9 : 11);
            heading += Mth.clamp(diff * 0.3, -maxTurn, maxTurn);
            // a sharp turn first, then the stride: slow down while the turn is large
            target *= Mth.clamp(Math.cos(Math.toRadians(Math.min(Math.abs(diff), 90))), 0.12, 1.0);
        }
        double accel = run ? 0.03 : 0.018;
        v += Mth.clamp(target - v, -0.035, accel);
        if (Math.abs(v) < 0.002 && target == 0) {
            v = 0;
        }
        if (v != 0) {
            double rad = Math.toRadians(heading);
            double nx = e.getX() - Math.sin(rad) * v;
            double nz = e.getZ() + Math.cos(rad) * v;
            double y = e.getY();
            double top = surface(level, nx, nz, y);
            if (top - y > 1.3) {
                v = 0; // a wall: stand
                nx = e.getX();
                nz = e.getZ();
                top = surface(level, nx, nz, y);
            }
            if (top > y) {
                y = Math.min(top, y + 0.22);
                vy = 0;
            } else if (top < y) {
                vy = Math.min(vy + 0.08, 0.7);
                y = Math.max(top, y - vy);
            } else {
                vy = 0;
            }
            e.setPos(nx, y, nz);
            e.setDeltaMovement(Vec3.ZERO);
            e.setOnGround(true);
            l.setSprinting(run && v > 0.16);
            if (!backing) {
                bodyYaw = heading;
            }
        } else {
            l.setSprinting(false);
        }
        // standing: the body turns slowly to where it is asked to face, or follows the head
        if (v == 0 || backing) {
            if (faceYaw != null) {
                bodyYaw += Mth.clamp(Mth.wrapDegrees(faceYaw - bodyYaw) * 0.2, -7, 7);
            } else if (!Double.isNaN(headYaw)) {
                double diff = Mth.wrapDegrees(headYaw - bodyYaw);
                if (Math.abs(diff) > 40) {
                    bodyYaw += Mth.clamp((diff - Math.signum(diff) * 40) * 0.3, -6, 6);
                }
            }
            heading = bodyYaw;
        }
        l.setYRot((float) bodyYaw);
        l.yBodyRot = (float) bodyYaw;
    }

    private void head(LivingEntity l) {
        double ty;
        double tp;
        if (look != null) {
            Vec3 d = look.get().subtract(l.getEyePosition());
            ty = Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG - 90.0;
            tp = -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        } else if (faceYaw != null && Math.abs(v) < 0.02) {
            ty = faceYaw;
            tp = 4;
        } else {
            ty = bodyYaw;
            tp = Math.abs(v) > 0.02 ? 6 : 3;
        }
        // a person is never perfectly still: small slow drifts of the gaze
        double s = age / 20.0 + seed;
        double amp = look != null ? 2.5 : 5.0;
        ty += amp * (0.6 * Math.sin(s * 0.9) + 0.4 * Math.sin(s * 2.3 + 1.7));
        tp += 1.8 * Math.sin(s * 1.3 + 0.4) + 0.8 * Math.sin(s * 3.1);
        if (Double.isNaN(headYaw)) {
            headYaw = l.yHeadRot;
            headPitch = l.getXRot();
        }
        // the head turns within the shoulders: at most 75 degrees off the body
        double rel = Mth.clamp(Mth.wrapDegrees(ty - bodyYaw), -75, 75);
        double want = bodyYaw + rel;
        headYaw += Mth.clamp(Mth.wrapDegrees(want - headYaw) * 0.22, -13, 13);
        headPitch += Mth.clamp((Mth.clamp(tp, -60, 70) - headPitch) * 0.22, -9, 9);
        l.yHeadRot = (float) headYaw;
        l.setXRot((float) headPitch);
    }

    /** Height of the walkable top at x/z near the given feet height (paths, slabs, farmland included). */
    private static double surface(ServerLevel level, double x, double z, double feet) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        for (int y = Mth.floor(feet + 1.4); y >= Mth.floor(feet - 4); y--) {
            m.set(bx, y, bz);
            BlockState st = level.getBlockState(m);
            if (st.isAir()) {
                continue;
            }
            VoxelShape sh = st.getCollisionShape(level, m);
            if (!sh.isEmpty()) {
                return y + sh.max(Direction.Axis.Y);
            }
        }
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }

    // ------------------------------------------------------------------ mobs with their own legs (giant, golem, chickens)

    private void mobTick(Mob mob) {
        if (mark != null) {
            Vec3 d = mark.subtract(e.position());
            if (Math.sqrt(d.x * d.x + d.z * d.z) < 0.5) {
                mark = null;
                mob.getNavigation().stop();
                mob.getMoveControl().setWantedPosition(e.getX(), e.getY(), e.getZ(), 0);
            } else {
                mob.getMoveControl().setWantedPosition(mark.x, mark.y, mark.z, speed);
            }
        }
        if (mark == null && faceYaw != null) {
            float b = mob.yBodyRot + Mth.clamp(Mth.wrapDegrees(faceYaw - mob.yBodyRot) * 0.15F, -5F, 5F);
            mob.setYRot(b);
            mob.yBodyRot = b;
        }
        if (look != null) {
            Vec3 p = look.get();
            mob.getLookControl().setLookAt(p.x, p.y, p.z, 8F, 20F);
        }
    }
}
