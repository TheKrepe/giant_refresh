package dev.star1mp.giantrefresh.cinema;

import dev.star1mp.giantrefresh.entity.GiantEntity;
import dev.star1mp.giantrefresh.entity.GiantRiders;
import dev.star1mp.giantrefresh.registry.GRContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One continuous camera take of the trailer, scripted in seconds relative to its origin. */
public abstract class Shot {
    public double duration = 5.0;
    public int siteX;
    public int siteZ;
    public boolean village;
    public long dayTime = 6000;
    /** Game seconds per film second (0.5 = slow motion). */
    public double timeScale = 1.0;
    public final Cam cam = new Cam();
    public BlockPos origin;
    public ServerLevel level;
    public final List<Actor> actors = new ArrayList<>();
    private final List<Object[]> cues = new ArrayList<>();
    private final List<Object[]> spans = new ArrayList<>();

    /** Build the set and the cast; {@link #origin} is known. Cues and the camera are defined here too. */
    protected abstract void build();

    // ------------------------------------------------------------------ timeline
    protected void on(double sec, Runnable r) { cues.add(new Object[] {(int) Math.round(sec * 20), r}); }
    /** Runs every tick between a and b (seconds) with the time in seconds. */
    protected void during(double a, double b, Consumer<Double> r) { spans.add(new Object[] {(int) Math.round(a * 20), (int) Math.round(b * 20), r}); }

    @SuppressWarnings("unchecked")
    void tick(int t) {
        for (Object[] c : cues) {
            if ((int) c[0] == t) {
                try {
                    ((Runnable) c[1]).run();
                } catch (RuntimeException ex) {
                    dev.star1mp.giantrefresh.GiantRefresh.LOGGER.error("[CINEMA] cue failed", ex);
                }
            }
        }
        for (Object[] s : spans) {
            if (t >= (int) s[0] && t <= (int) s[1]) {
                ((Consumer<Double>) s[2]).accept(t / 20.0);
            }
        }
        for (Actor a : actors) {
            a.tick(level);
        }
    }

    // ------------------------------------------------------------------ places
    protected Vec3 o(double dx, double dy, double dz) {
        return new Vec3(origin.getX() + 0.5 + dx, origin.getY() + dy, origin.getZ() + 0.5 + dz);
    }

    /** Ground point (feet height) at an offset. */
    protected Vec3 g(double dx, double dz) {
        int x = origin.getX() + (int) Math.floor(dx + 0.5);
        int z = origin.getZ() + (int) Math.floor(dz + 0.5);
        return new Vec3(origin.getX() + 0.5 + dx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), origin.getZ() + 0.5 + dz);
    }

    protected BlockPos b(int dx, int dy, int dz) { return origin.offset(dx, dy, dz); }
    protected void set(int dx, int dy, int dz, BlockState st) { level.setBlock(b(dx, dy, dz), st, 3); }

    protected void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState st) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    level.setBlock(b(x, y, z), st, 2);
                }
            }
        }
    }

    protected void cmd(String c) { Cinema.command(level.getServer(), c); }

    // ------------------------------------------------------------------ cast
    protected <T extends Entity> Actor spawn(EntityType<T> type, Vec3 at, float yaw) {
        T e = type.create(level, EntitySpawnReason.COMMAND);
        e.snapTo(at.x, at.y, at.z, yaw, 0.0F);
        if (e instanceof LivingEntity l) {
            l.yHeadRot = yaw;
            l.yBodyRot = yaw;
        }
        level.addFreshEntity(e);
        Actor a = new Actor(e);
        actors.add(a);
        return a;
    }

    protected Actor giant(Vec3 at, float yaw, int hat, boolean riders) {
        GiantEntity g = GRContent.GIANT.get().create(level, EntitySpawnReason.COMMAND);
        g.snapTo(at.x, at.y, at.z, yaw, 0.0F);
        g.yHeadRot = yaw;
        g.yBodyRot = yaw;
        g.setHatColor(hat);
        if (riders && hat >= 0) {
            GiantRiders.spawnRiders(g, level, level.getCurrentDifficultyAt(g.blockPosition()));
            for (Entity p : g.getPassengers()) {
                if (p instanceof Mob m) {
                    m.setPersistenceRequired();
                }
            }
        }
        level.addFreshEntityWithPassengers(g);
        Actor a = new Actor(g);
        actors.add(a);
        return a;
    }

    /** A player-like actor (mannequin) with a default skin: "steve", "alex", ... */
    protected Actor person(String skin, Vec3 at, float yaw) {
        String tag = "cine_" + actors.size();
        String model = skin.equals("alex") || skin.equals("ari") || skin.equals("efe") || skin.equals("makena") || skin.equals("sunny") ? "slim" : "wide";
        Cinema.command(level.getServer(), String.format(java.util.Locale.ROOT,
                "summon minecraft:mannequin %.3f %.3f %.3f {Rotation:[%.1ff,0f],hide_description:1b,profile:{texture:\"minecraft:entity/player/%s/%s\",model:\"%s\"},Tags:[\"%s\"]}",
                at.x, at.y, at.z, yaw, model, skin, model, tag));
        List<Entity> found = level.getEntities((Entity) null, new AABB(at, at).inflate(2), e -> e.entityTags().contains(tag));
        Entity e;
        if (found.isEmpty()) {
            dev.star1mp.giantrefresh.GiantRefresh.LOGGER.warn("[CINEMA] mannequin with skin failed, plain one");
            Cinema.command(level.getServer(), String.format(java.util.Locale.ROOT,
                    "summon minecraft:mannequin %.3f %.3f %.3f {Rotation:[%.1ff,0f],hide_description:1b,Tags:[\"%s\"]}", at.x, at.y, at.z, yaw, tag));
            found = level.getEntities((Entity) null, new AABB(at, at).inflate(2), x -> x.entityTags().contains(tag));
        }
        e = found.get(0);
        if (e instanceof LivingEntity l) {
            l.yHeadRot = yaw;
            l.yBodyRot = yaw;
        }
        Actor a = new Actor(e);
        actors.add(a);
        return a;
    }

    /** Any entity by summon command (for NBT such as villager professions); found again by a tag. */
    protected Actor summon(String id, Vec3 at, float yaw, String nbt) {
        String tag = "cine_s" + actors.size();
        String extra = nbt == null || nbt.isEmpty() ? "" : nbt + ",";
        Cinema.command(level.getServer(), String.format(java.util.Locale.ROOT, "summon %s %.3f %.3f %.3f {%sRotation:[%.1ff,0f],Tags:[\"%s\"]}",
                id, at.x, at.y, at.z, extra, yaw, tag));
        List<Entity> found = level.getEntities((Entity) null, new AABB(at, at).inflate(3), e -> e.entityTags().contains(tag));
        if (found.isEmpty()) {
            dev.star1mp.giantrefresh.GiantRefresh.LOGGER.warn("[CINEMA] summon failed: {} {}", id, nbt);
            return null;
        }
        Entity e = found.get(0);
        if (e instanceof LivingEntity l) {
            l.yHeadRot = yaw;
            l.yBodyRot = yaw;
        }
        Actor a = new Actor(e);
        actors.add(a);
        return a;
    }

    private final List<double[]> thumps = new ArrayList<>();
    {
        cam.shake(t -> {
            double s = 0;
            for (double[] th : thumps) {
                double d = t - th[0];
                if (d >= 0 && d < 1.2) {
                    s += th[1] * Math.exp(-d * 5.0);
                }
            }
            return s;
        });
    }

    /** A camera jolt at a time (seconds) with strength in degrees. */
    protected void jolt(double sec, double strength) { thumps.add(new double[] {sec, strength}); }

    protected void particles(net.minecraft.core.particles.ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(p, at.x, at.y, at.z, count, spread, spread * 0.4, spread, speed);
    }

    protected void dust(Vec3 at, int count, double spread) {
        particles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()), at, count, spread, 0.15);
        particles(net.minecraft.core.particles.ParticleTypes.POOF, at, count / 3, spread, 0.03);
    }

    protected void sound(net.minecraft.sounds.SoundEvent ev, Vec3 at, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, ev, net.minecraft.sounds.SoundSource.HOSTILE, vol, pitch);
    }

    protected void sound(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> ev, Vec3 at, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, ev, net.minecraft.sounds.SoundSource.NEUTRAL, vol, pitch);
    }

    /** Opens or closes the door whose lower half is at p. */
    protected void door(BlockPos p, boolean open) {
        var st = level.getBlockState(p);
        if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock d) {
            d.setOpen(null, level, st, p, open);
        }
    }

    /** Camera target that follows an entity (smoothly interpolated on the client). */
    protected DoubleFunction<Vec3> track(Actor a, double dy) {
        int id = a.e.getId();
        return t -> CinemaClientBridge.entityPos(id, dy, a.e.position().add(0, dy, 0));
    }

    protected DoubleFunction<Vec3> track(Entity e, double dy) {
        int id = e.getId();
        return t -> CinemaClientBridge.entityPos(id, dy, e.position().add(0, dy, 0));
    }

    protected GiantEntity giantOf(Actor a) { return (GiantEntity) a.e; }

    protected static void forEach(List<Actor> list, BiConsumer<Integer, Actor> f) {
        for (int i = 0; i < list.size(); i++) {
            f.accept(i, list.get(i));
        }
    }
}
