package dev.star1mp.giantrefresh.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.star1mp.giantrefresh.config.GRConfig;
import dev.star1mp.giantrefresh.entity.GiantEntity;
import dev.star1mp.giantrefresh.entity.GiantRiders;
import dev.star1mp.giantrefresh.registry.GRContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RotatedPillarBlock;
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
        // the oldest end of some trails starts at a little settlement the Giant walked through
        if (random.nextDouble() < GRConfig.get().trailRuinChance) {
            double startAlong = -length * scale / 2.0 - 6.0;
            placeRuin(level, random, centerX + dirX * startAlong, centerZ + dirZ * startAlong, dirX, dirZ, sideX, sideZ, true);
            if (random.nextBoolean()) {
                double side = random.nextBoolean() ? 7.0 : -7.0;
                placeRuin(level, random, centerX + dirX * (startAlong + 2.0) + sideX * side,
                        centerZ + dirZ * (startAlong + 2.0) + sideZ * side, dirX, dirZ, sideX, sideZ, false);
            }
        }
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

    /**
     * A small wooden hut, trampled: broken log corners, walls knocked down to stumps, rubble and
     * debris scattered around, and (for the main hut) a Giant footprint stamped right through it.
     */
    private void placeRuin(WorldGenLevel level, RandomSource random, double cx, double cz,
                           double dirX, double dirZ, double sideX, double sideZ, boolean stepped) {
        int bx = Mth.floor(cx);
        int bz = Mth.floor(cz);
        int baseY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        // only on fairly flat, dry ground that we are allowed to write
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx + dx, bz + dz);
                BlockPos ground = new BlockPos(bx + dx, h - 1, bz + dz);
                if (Math.abs(h - baseY) > 1 || !level.ensureCanWrite(ground)
                        || !level.getFluidState(ground).isEmpty() || !level.getBlockState(ground).is(GRContent.FOOTPRINT_REPLACEABLE)) {
                    return;
                }
            }
        }
        // cells crushed by the foot
        java.util.Set<Long> crushed = new java.util.HashSet<>();
        double footX = cx + sideX * 0.6;
        double footZ = cz + sideZ * 0.6;
        if (stepped) {
            for (int row = 0; row < FOOT.length; row++) {
                for (int col = 0; col < 3; col++) {
                    if (FOOT[row][col] != 0) {
                        int x = Mth.floor(footX + dirX * (2 - row) + sideX * (col - 1));
                        int z = Mth.floor(footZ + dirZ * (2 - row) + sideZ * (col - 1));
                        crushed.add(BlockPos.asLong(x, 0, z));
                    }
                }
            }
        }

        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = bx + dx;
                int z = bz + dz;
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                p.set(x, ground + 1, z);
                if (level.getBlockState(p).is(GRContent.GIANT_CRUSHABLE)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
                boolean border = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                boolean isCrushed = crushed.contains(BlockPos.asLong(x, 0, z));
                p.set(x, ground, z);
                if (isCrushed) {
                    continue; // the foot comes down here: stamped below
                }
                if (border) {
                    level.setBlock(p, (random.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState(), 2);
                } else if (random.nextInt(3) != 0) {
                    level.setBlock(p, (random.nextInt(4) == 0 ? Blocks.GRAVEL : Blocks.COARSE_DIRT).defaultBlockState(), 2);
                }
                if (!border) {
                    continue;
                }
                int height = corner ? 1 + random.nextInt(3) : random.nextInt(3);
                for (int i = 1; i <= height; i++) {
                    p.set(x, ground + i, z);
                    BlockState state;
                    if (corner) {
                        state = Blocks.OAK_LOG.defaultBlockState();
                    } else if (i == 1 && random.nextInt(3) == 0) {
                        state = (random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState();
                    } else {
                        state = Blocks.OAK_PLANKS.defaultBlockState();
                    }
                    level.setBlock(p, state, 2);
                }
                if (height > 0 && !corner && random.nextInt(4) == 0) {
                    p.set(x, ground + height + 1, z);
                    level.setBlock(p, Blocks.OAK_SLAB.defaultBlockState(), 2);
                }
            }
        }
        // debris: a fallen beam, planks and slabs thrown around, the odd piece of furniture
        scatter(level, random, bx, bz, 4, Blocks.OAK_SLAB.defaultBlockState(), 4);
        scatter(level, random, bx, bz, 4, Blocks.COBBLESTONE.defaultBlockState(), 2);
        if (random.nextBoolean()) {
            scatter(level, random, bx, bz, 1, (random.nextBoolean() ? Blocks.CRAFTING_TABLE : Blocks.BARREL).defaultBlockState(), 1);
        }
        if (random.nextInt(3) == 0) {
            scatter(level, random, bx, bz, 1, Blocks.COBWEB.defaultBlockState(), 1);
        }
        if (random.nextBoolean()) {
            Direction.Axis axis = Math.abs(dirX) > Math.abs(dirZ) ? Direction.Axis.Z : Direction.Axis.X;
            BlockState beam = Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
            int ox = bx + (axis == Direction.Axis.X ? -1 : 3);
            int oz = bz + (axis == Direction.Axis.X ? 3 : -1);
            for (int i = 0; i < 3; i++) {
                int x = ox + (axis == Direction.Axis.X ? i : 0);
                int z = oz + (axis == Direction.Axis.Z ? i : 0);
                placeOnGround(level, x, z, beam);
            }
        }
        if (stepped) {
            stampFoot(level, random, footX, footZ, dirX, dirZ, sideX, sideZ, true, 0.8);
        }
    }

    /** CI render test: a stepped-on hut and a second, plain one next to it. */
    public static void placeTestRuins(WorldGenLevel level, RandomSource random, int x, int z) {
        GiantTrailFeature feature = new GiantTrailFeature(4, 8, 0.0F, 0.8F);
        feature.placeRuin(level, random, x + 0.5, z + 0.5, 0.0, 1.0, -1.0, 0.0, true);
        feature.placeRuin(level, random, x + 0.5, z + 8.5, 0.0, 1.0, -1.0, 0.0, false);
    }

    private static void scatter(WorldGenLevel level, RandomSource random, int cx, int cz, int radius, BlockState state, int count) {
        for (int i = 0; i < count; i++) {
            int x = cx + random.nextInt(radius * 2 + 1) - radius;
            int z = cz + random.nextInt(radius * 2 + 1) - radius;
            placeOnGround(level, x, z, state);
        }
    }

    private static void placeOnGround(WorldGenLevel level, int x, int z, BlockState state) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.ensureCanWrite(pos)) {
            return;
        }
        BlockState here = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());
        // only on the ground or the hut floor, never on top of a wall stump
        boolean floor = below.is(GRContent.FOOTPRINT_REPLACEABLE) || below.is(Blocks.GRAVEL) || below.is(Blocks.COARSE_DIRT);
        if ((here.isAir() || here.is(GRContent.GIANT_CRUSHABLE)) && here.getFluidState().isEmpty() && floor) {
            level.setBlock(pos, state, 2);
        }
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
