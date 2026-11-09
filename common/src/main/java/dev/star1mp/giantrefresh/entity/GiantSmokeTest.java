package dev.star1mp.giantrefresh.entity;

import dev.star1mp.giantrefresh.GiantRefresh;
import dev.star1mp.giantrefresh.registry.GRContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * CI-only self test, enabled with {@code -Dgiantrefresh.smoketest=true} on a dedicated server:
 * spawns Giants (with hat/riders), a vanilla Giant (must be replaced) and a zombie under the
 * Growth effect, lets them tick for a while, logs the result and stops the server.
 */
public final class GiantSmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("giantrefresh.smoketest");
    private static int ticks;
    private static Zombie grower;

    private GiantSmokeTest() {}

    public static void tick(ServerLevel level) {
        if (!ENABLED || level.dimension() != Level.OVERWORLD) {
            return;
        }
        ticks++;
        if (ticks == 1) {
            for (int cx = -2; cx <= 3; cx++) {
                for (int cz = -2; cz <= 3; cz++) {
                    level.setChunkForced(cx, cz, true);
                }
            }
        }
        BlockPos base = new BlockPos(8, 0, 8);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ());
        BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
        if (ticks == 100) {
            GiantRefresh.LOGGER.info("[SMOKETEST] spawning test entities at {}", pos);
            for (int i = 0; i < 3; i++) {
                GiantEntity g = GRContent.GIANT.get().spawn(level, pos.offset(i * 8, 0, 0), EntitySpawnReason.SPAWN_ITEM_USE);
                if (g != null) {
                    g.setHatColor(i * 5);
                    GiantRiders.spawnRiders(g, level, level.getCurrentDifficultyAt(pos));
                    GiantRefresh.LOGGER.info("[SMOKETEST] giant {} riders={}", i, g.getPassengers().size());
                }
            }
            EntityTypes.GIANT.spawn(level, pos.offset(0, 0, 12), EntitySpawnReason.COMMAND);
            grower = EntityTypes.ZOMBIE.spawn(level, pos.offset(0, 0, -10), EntitySpawnReason.COMMAND);
            if (grower != null) {
                grower.addEffect(new MobEffectInstance(GRContent.growthHolder(), 200));
            }
            GiantEntity sleeper = GRContent.GIANT.get().spawn(level, pos.offset(-12, 0, 0), EntitySpawnReason.SPAWN_ITEM_USE);
            if (sleeper != null) {
                sleeper.setGiantSleeping(true);
            }
        }
        if (ticks == 220 && grower != null) {
            GiantRefresh.LOGGER.info("[SMOKETEST] zombie scale under Growth = {}", grower.getAttributeValue(Attributes.SCALE));
        }
        if (ticks == 500) {
            AABB area = new AABB(pos).inflate(96);
            int giants = level.getEntitiesOfClass(GiantEntity.class, area).size();
            int vanilla = level.getEntitiesOfClass(net.minecraft.world.entity.monster.Giant.class, area).size();
            int riders = 0;
            for (GiantEntity g : level.getEntitiesOfClass(GiantEntity.class, area)) {
                riders += g.getPassengers().size();
            }
            double scale = grower != null && grower.isAlive() ? grower.getAttributeValue(Attributes.SCALE) : -1;
            boolean pass = giants >= 4 && vanilla == 0;
            GiantRefresh.LOGGER.info("[SMOKETEST] giants={} vanillaGiants={} riders={} zombieScaleAfterEffect={} -> {}",
                    giants, vanilla, riders, scale, pass ? "PASS" : "FAIL");
            level.getServer().halt(false);
        }
    }
}
