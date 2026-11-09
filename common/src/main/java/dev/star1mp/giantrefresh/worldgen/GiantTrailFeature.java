package dev.star1mp.giantrefresh.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.star1mp.giantrefresh.config.GRConfig;
import dev.star1mp.giantrefresh.entity.GiantEntity;
import dev.star1mp.giantrefresh.entity.GiantRiders;
import dev.star1mp.giantrefresh.registry.GRContent;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.phys.AABB;

/**
 * A line of huge footprints. Along the trail the prints get fresher and closer together;
 * a "camp" trail may end with a Giant sleeping next to its lair.
 */
public record GiantTrailFeature(int minPrints, int maxPrints, float giantChance, float freshness) implements Feature {
    public static final MapCodec<GiantTrailFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(2, 12).fieldOf("min_prints").forGetter(GiantTrailFeature::minPrints),
            Codec.intRange(2, 12).fieldOf("max_prints").forGetter(GiantTrailFeature::maxPrints),
            Codec.floatRange(0.0F, 1.0F).fieldOf("giant_chance").forGetter(GiantTrailFeature::giantChance),
            Codec.floatRange(0.0F, 1.0F).fieldOf("freshness").forGetter(GiantTrailFeature::freshness)
    ).apply(instance, GiantTrailFeature::new));

    /** Foot shape, toe row first: 5 rows (along the step) × 3 columns (across). */
    private static final int[][] FOOT = {
            {1, 0, 1},
            {1, 1, 1},
            {1, 1, 1},
            {0, 1, 1},
            {0, 1, 0},
    };

    @Override
    public MapCodec<? extends Feature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (!GRConfig.get().worldgenTrails) {
            return false;
        }
        // Centre on the chunk so the whole trail stays inside the writable 3x3 chunk area.
        int centerX = (origin.getX() & ~15) + 8;
        int centerZ = (origin.getZ() & ~15) + 8;
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dirX = Math.cos(angle);
        double dirZ = Math.sin(angle);
        double sideX = -dirZ;
        double sideZ = dirX;

        int count = minPrints + random.nextInt(Math.max(1, maxPrints - minPrints + 1));
        double[] gaps = new double[count];
        double length = 0;
        for (int i = 1; i < count; i++) {
            double t = i / (double) (count - 1);
            gaps[i] = Mth.lerp(t, 4.6, 3.0); // prints come closer together towards the fresh end
            length += gaps[i];
        }
        double scale = length > 22.0 ? 22.0 / length : 1.0;
        double along = -length * scale / 2.0;
        boolean placedAny = false;
        double lastX = centerX;
        double lastZ = centerZ;
        for (int i = 0; i < count; i++) {
            along += gaps[i] * scale;
            double t = count > 1 ? i / (double) (count - 1) : 1.0;
            double fresh = Mth.clamp(freshness - 0.45 + 0.55 * t, 0.0, 1.0);
            boolean right = (i & 1) == 0;
            double side = right ? 1.1 : -1.1;
            double px = centerX + dirX * along + sideX * side;
            double pz = centerZ + dirZ * along + sideZ * side;
            placedAny |= stampFoot(level, random, px, pz, dirX, dirZ, sideX, sideZ, right, fresh);
            lastX = px - sideX * side;
            lastZ = pz - sideZ * side;
        }
        if (placedAny && random.nextFloat() < giantChance) {
            placeSleepingGiant(level, random, lastX + dirX * 4.0, lastZ + dirZ * 4.0, dirX, dirZ);
        }
        return placedAny;
    }

    private boolean stampFoot(WorldGenLevel level, RandomSource random, double cx, double cz,
                              double dirX, double dirZ, double sideX, double sideZ, boolean rightFoot, double fresh) {
        boolean any = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int row = 0; row < FOOT.length; row++) {
            double u = 2 - row; // toe at +2, heel at -2
            for (int col = 0; col < 3; col++) {
                int mask = FOOT[row][rightFoot ? col : 2 - col];
                if (mask == 0) {
                    continue;
                }
                double v = col - 1;
                int x = Mth.floor(cx + dirX * u + sideX * v);
                int z = Mth.floor(cz + dirZ * u + sideZ * v);
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                pos.set(x, surface, z);
                if (!level.ensureCanWrite(pos)) {
                    continue;
                }
                BlockState ground = level.getBlockState(pos);
                if (!ground.is(GRContent.FOOTPRINT_REPLACEABLE)) {
                    continue;
                }
                BlockPos above = pos.above();
                BlockState plant = level.getBlockState(above);
                if (!plant.isAir()) {
                    if (plant.is(GRContent.GIANT_CRUSHABLE) && plant.getFluidState().isEmpty()) {
                        level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
                    } else {
                        continue; // something solid (a log, a rock) – leave it alone
                    }
                }
                if (fresh > 0.66 && !hasWaterNear(level, pos)) {
                    // deep, fresh print: the turf is pressed down a whole block
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    BlockPos below = pos.below();
                    if (level.getBlockState(below).is(GRContent.FOOTPRINT_REPLACEABLE)) {
                        level.setBlock(below, (random.nextInt(4) == 0 ? Blocks.ROOTED_DIRT : Blocks.DIRT).defaultBlockState(), 2);
                    }
                } else if (fresh > 0.33) {
                    level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), 2);
                } else if (random.nextFloat() < 0.65F) {
                    // old print, grass is slowly growing back
                    level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), 2);
                }
                any = true;
            }
        }
        return any;
    }

    private static boolean hasWaterNear(WorldGenLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 1, 1))) {
            if (!level.getFluidState(p).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void placeSleepingGiant(WorldGenLevel level, RandomSource random, double x, double z, double dirX, double dirZ) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        BlockPos pos = new BlockPos(bx, y, bz);
        if (!level.ensureCanWrite(pos) || !level.getFluidState(pos.below()).isEmpty()) {
            return;
        }
        AABB box = new AABB(x - 1.8, y + 0.1, z - 1.8, x + 1.8, y + 8.6, z + 1.8);
        if (!level.noCollision(box)) {
            return;
        }
        // a trampled patch – the Giant's lair
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 10 || random.nextInt(5) == 0) {
                    continue;
                }
                int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx + dx, bz + dz) - 1;
                p.set(bx + dx, sy, bz + dz);
                if (level.ensureCanWrite(p) && level.getBlockState(p).is(GRContent.FOOTPRINT_REPLACEABLE)) {
                    BlockState plant = level.getBlockState(p.above());
                    if (plant.is(GRContent.GIANT_CRUSHABLE) && plant.getFluidState().isEmpty()) {
                        level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 2);
                    }
                    if (level.getBlockState(p.above()).isAir()) {
                        level.setBlock(p, (random.nextInt(3) == 0 ? Blocks.PODZOL : Blocks.COARSE_DIRT).defaultBlockState(), 2);
                    }
                }
            }
        }
        GiantEntity giant = GRContent.GIANT.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (giant == null) {
            return;
        }
        // sits facing back along its own trail
        float yaw = (float) Math.toDegrees(Math.atan2(dirX, -dirZ));
        giant.snapTo(x, y, z, yaw, 0.0F);
        giant.yHeadRot = yaw;
        giant.yBodyRot = yaw;
        giant.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
        giant.setGiantSleeping(true);
        GiantRiders.makePersistent(giant);
        level.addFreshEntityWithPassengers(giant);
    }
}
