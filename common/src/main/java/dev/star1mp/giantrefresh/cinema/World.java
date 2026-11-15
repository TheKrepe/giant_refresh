package dev.star1mp.giantrefresh.cinema;

import dev.star1mp.giantrefresh.registry.GRContent;
import dev.star1mp.giantrefresh.worldgen.GiantTrailFeature;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * The trailer's world, built the same way for every shot so all takes match: a small plains
 * village, a footprint trail leading north to a trampled hut, the Giant's camp, and Steve's tower.
 * Coordinates are relative to the village centre (the shot origin); +x east, +z south.
 */
public final class World {
    /** Village ground height (absolute). */
    public final int y;
    public final BlockPos c;
    private final ServerLevel level;
    public final List<BlockPos> doors = new ArrayList<>();
    /** Footprint centres along the trail (relative x,z), oldest first. */
    public final List<double[]> prints = new ArrayList<>();
    public static final double TRAIL_X = -4;
    public static final double TRAIL_Z0 = -40;
    public static final double TRAIL_Z1 = -82;
    public static final int RUIN_X = -10;
    public static final int RUIN_Z = -96;
    public static final int CAMP_X = 2;
    public static final int CAMP_Z = -128;
    public static final int TOWER_X = -27;
    public static final int TOWER_Z = 6;
    public static final int FLOOR = 8; // tower upper floor height above ground

    public World(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.c = origin;
        this.y = origin.getY();
    }

    public int ground(double dx, double dz) {
        int x = c.getX() + (int) Math.floor(dx);
        int z = c.getZ() + (int) Math.floor(dz);
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    public Vec3 at(double dx, double dz) {
        return new Vec3(c.getX() + 0.5 + dx, ground(dx, dz), c.getZ() + 0.5 + dz);
    }

    public void build() {
        for (int cx = -6; cx <= 4; cx++) {
            for (int cz = -11; cz <= 4; cz++) {
                level.getChunk((c.getX() >> 4) + cx, (c.getZ() >> 4) + cz);
            }
        }
        village();
        trail();
        ruins();
        camp();
        tower();
    }

    // ------------------------------------------------------------------ ground shaping

    /** Flat at height h inside r1, blending back to the natural ground by r2. */
    private void pad(int cx, int cz, int r1, int r2, int h) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r2; dx <= r2; dx++) {
            for (int dz = -r2; dz <= r2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r2) {
                    continue;
                }
                int x = c.getX() + cx + dx;
                int z = c.getZ() + cz + dz;
                int nat = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                double k = d <= r1 ? 1.0 : 1.0 - (d - r1) / (r2 - r1);
                k = k * k * (3 - 2 * k);
                int want = (int) Math.round(nat + (h - nat) * k);
                for (int yy = Math.max(top, want + 12); yy >= want; yy--) {
                    m.set(x, yy, z);
                    BlockState st = level.getBlockState(m);
                    if (!st.isAir() && (d <= r1 || !st.is(net.minecraft.tags.BlockTags.LOGS) || yy < want + 12)) {
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
                for (int yy = want - 1; yy >= want - 8; yy--) {
                    m.set(x, yy, z);
                    BlockState st = level.getBlockState(m);
                    if (yy < want - 2 && !st.isAir() && st.getFluidState().isEmpty()) {
                        break;
                    }
                    level.setBlock(m, yy == want - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2 | 16);
                }
            }
        }
    }

    private void plants(int cx, int cz, int r, double grass, double flowers, long seed, boolean tall) {
        RandomSource rnd = RandomSource.create(seed);
        BlockState[] fl = {Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(),
                Blocks.CORNFLOWER.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState()};
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                int x = c.getX() + cx + dx;
                int z = c.getZ() + cz + dz;
                m.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (!level.getBlockState(m).isAir() || !level.getBlockState(m.below()).is(Blocks.GRASS_BLOCK)) {
                    continue;
                }
                double v = rnd.nextDouble();
                if (v < flowers) {
                    level.setBlock(m, fl[rnd.nextInt(fl.length)], 2);
                } else if (v < flowers + grass) {
                    if (tall && rnd.nextInt(3) == 0) {
                        level.setBlock(m, Blocks.TALL_GRASS.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), 2);
                        level.setBlock(m.above(), Blocks.TALL_GRASS.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 2);
                    } else {
                        level.setBlock(m, Blocks.SHORT_GRASS.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private void path(double x1, double z1, double x2, double z2) {
        double len = Math.hypot(x2 - x1, z2 - z1);
        for (double d = 0; d <= len; d += 0.5) {
            double x = x1 + (x2 - x1) * d / len;
            double z = z1 + (z2 - z1) * d / len;
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    BlockPos p = new BlockPos(c.getX() + (int) Math.floor(x) + ox, y - 1, c.getZ() + (int) Math.floor(z) + oz);
                    if (level.getBlockState(p).is(Blocks.GRASS_BLOCK)) {
                        level.setBlock(p, Blocks.DIRT_PATH.defaultBlockState(), 2);
                        if (!level.getBlockState(p.above()).isAir() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
                            level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ village

    /** Places a template so its footprint is centred on (cx, cz). */
    private BoundingBox house(String id, int cx, int cz, Rotation rot) {
        var opt = level.getStructureManager().get(Identifier.parse("minecraft:village/plains/" + id));
        if (opt.isEmpty()) {
            return null;
        }
        StructureTemplate t = opt.get();
        StructurePlaceSettings s = new StructurePlaceSettings().setRotation(rot).setIgnoreEntities(true).addProcessor(JigsawReplacementProcessor.INSTANCE);
        BlockPos probe = new BlockPos(c.getX() + cx, y, c.getZ() + cz);
        BoundingBox b0 = t.getBoundingBox(s, probe);
        BlockPos at = probe.offset(probe.getX() - (b0.minX() + b0.maxX()) / 2, 0, probe.getZ() - (b0.minZ() + b0.maxZ()) / 2);
        // templates sit with their floor one block into the ground
        at = at.below();
        t.placeInWorld(level, at, at, s, RandomSource.create(1), 2);
        BoundingBox box = t.getBoundingBox(s, at);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int yy = box.minY(); yy <= box.maxY(); yy++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockState st = level.getBlockState(m.set(x, yy, z));
                    if (st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && yy <= y + 1) {
                        doors.add(m.immutable());
                    }
                }
            }
        }
        return box;
    }

    private void village() {
        pad(0, 0, 34, 46, y);
        house("town_centers/plains_fountain_01", 0, 0, Rotation.NONE);
        house("houses/plains_small_house_1", -15, -1, Rotation.CLOCKWISE_180);
        house("houses/plains_medium_house_1", 16, -2, Rotation.NONE);
        house("houses/plains_small_house_3", 2, -16, Rotation.COUNTERCLOCKWISE_90);
        house("houses/plains_big_house_1", -1, 16, Rotation.CLOCKWISE_90);
        house("houses/plains_small_house_7", 15, -16, Rotation.NONE);
        house("houses/plains_small_house_5", 14, 15, Rotation.NONE);
        house("houses/plains_small_farm_1", -14, -15, Rotation.NONE);
        house("houses/plains_large_farm_1", -16, 16, Rotation.CLOCKWISE_90);
        house("plains_lamp_1", 6, 6, Rotation.NONE);
        house("plains_lamp_1", -6, -7, Rotation.NONE);
        path(0, -6, 0, -30);
        path(-6, 0, -11, 0);
        path(6, 0, 10, 0);
        path(0, 6, 0, 10);
        path(0, -30, TRAIL_X, TRAIL_Z0);
        plants(0, 0, 44, 0.16, 0.035, 11, false);
    }

    // ------------------------------------------------------------------ trail, ruins, camp

    private void trail() {
        RandomSource r = RandomSource.create(5);
        int n = 0;
        for (double z = TRAIL_Z0; z >= TRAIL_Z1; z -= 4.2) {
            boolean right = (n & 1) == 0;
            double x = TRAIL_X + (right ? 1.1 : -1.1) + Math.sin(z * 0.07) * 1.5;
            double fresh = 0.55 + 0.45 * (TRAIL_Z0 - z) / (TRAIL_Z0 - TRAIL_Z1);
            GiantTrailFeature.cinemaFoot(level, r, c.getX() + 0.5 + x, c.getZ() + 0.5 + z, 0.0, -1.0, right, fresh);
            prints.add(new double[] {x, z});
            n++;
        }
        plants((int) TRAIL_X, (int) ((TRAIL_Z0 + TRAIL_Z1) / 2), 26, 0.22, 0.05, 21, false);
    }

    private void ruins() {
        pad(RUIN_X, RUIN_Z, 7, 12, ground(RUIN_X, RUIN_Z));
        RandomSource r = RandomSource.create(9);
        GiantTrailFeature.cinemaRuin(level, r, c.getX() + RUIN_X + 0.5, c.getZ() + RUIN_Z + 0.5, 0.0, -1.0, true);
    }

    private void camp() {
        int h = ground(CAMP_X, CAMP_Z);
        pad(CAMP_X, CAMP_Z, 9, 16, h);
        BlockPos fire = new BlockPos(c.getX() + CAMP_X + 5, h, c.getZ() + CAMP_Z + 4);
        level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), 3);
        level.setBlock(fire.offset(1, 0, 2), Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X), 3);
        level.setBlock(fire.offset(-2, 0, 1), Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z), 3);
        level.setBlock(fire.offset(2, 0, -2), Blocks.BONE_BLOCK.defaultBlockState(), 3);
        level.setBlock(fire.offset(-1, 0, -3), Blocks.HAY_BLOCK.defaultBlockState(), 3);
        plants(CAMP_X, CAMP_Z + 14, 12, 0.5, 0.03, 31, true);
        plants(CAMP_X, CAMP_Z, 16, 0.12, 0.0, 33, false);
    }

    public Vec3 campFire() {
        int h = ground(CAMP_X, CAMP_Z);
        return new Vec3(c.getX() + CAMP_X + 5.5, h + 0.5, c.getZ() + CAMP_Z + 4.5);
    }

    // ------------------------------------------------------------------ Steve's tower (epilogue)

    private void tower() {
        int bx = c.getX() + TOWER_X - 3;
        int bz = c.getZ() + TOWER_Z - 3;
        BlockState plank = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState cob = Blocks.COBBLESTONE.defaultBlockState();
        for (int dx = 0; dx <= 6; dx++) {
            for (int dz = 0; dz <= 6; dz++) {
                boolean edge = dx == 0 || dx == 6 || dz == 0 || dz == 6;
                boolean corner = (dx == 0 || dx == 6) && (dz == 0 || dz == 6);
                for (int dy = -1; dy <= 13; dy++) {
                    BlockPos p = new BlockPos(bx + dx, y + dy, bz + dz);
                    BlockState st;
                    if (dy == -1) {
                        st = cob;
                    } else if (dy == FLOOR - 1 && !edge) {
                        st = Blocks.OAK_PLANKS.defaultBlockState();
                    } else if (dy == 13) {
                        st = Blocks.SPRUCE_SLAB.defaultBlockState();
                    } else if (edge) {
                        st = corner ? log : (dy <= 1 ? cob : plank);
                    } else {
                        st = Blocks.AIR.defaultBlockState();
                    }
                    level.setBlock(p, st, 2);
                }
            }
        }
        // a roof ring of stairs
        for (int dx = -1; dx <= 7; dx++) {
            for (int dz = -1; dz <= 7; dz++) {
                if (dx == -1 || dx == 7 || dz == -1 || dz == 7) {
                    Direction f = dx == -1 ? Direction.EAST : dx == 7 ? Direction.WEST : dz == -1 ? Direction.SOUTH : Direction.NORTH;
                    level.setBlock(new BlockPos(bx + dx, y + 12, bz + dz), Blocks.SPRUCE_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, f).setValue(StairBlock.HALF, Half.BOTTOM), 2);
                }
            }
        }
        // door (west, ground floor), window (east, upper floor, 3 wide x 2 high), small north window
        level.setBlock(new BlockPos(bx, y, bz + 3), Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlock(new BlockPos(bx, y + 1, bz + 3), Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        for (int dz = 2; dz <= 4; dz++) {
            for (int dy = FLOOR + 1; dy <= FLOOR + 2; dy++) {
                level.setBlock(new BlockPos(bx + 6, y + dy, bz + dz), Blocks.GLASS_PANE.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.IronBarsBlock.NORTH, dz > 2).setValue(net.minecraft.world.level.block.IronBarsBlock.SOUTH, dz < 4), 2);
            }
        }
        level.setBlock(new BlockPos(bx + 3, y + FLOOR + 1, bz), Blocks.GLASS_PANE.defaultBlockState(), 2);
        // ladder up
        for (int dy = 0; dy < FLOOR; dy++) {
            level.setBlock(new BlockPos(bx + 1, y + dy, bz + 5), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH), 2);
        }
        level.setBlock(new BlockPos(bx + 1, y + FLOOR - 1, bz + 5), Blocks.AIR.defaultBlockState(), 2);
        // upper room
        int fy = y + FLOOR;
        level.setBlock(new BlockPos(bx + 2, fy, bz + 1), GRContent.GIANT_HEAD_BLOCK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH), 3);
        level.setBlock(new BlockPos(bx + 4, fy, bz + 1), Blocks.BREWING_STAND.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BrewingStandBlock.HAS_BOTTLE[0], true)
                .setValue(net.minecraft.world.level.block.BrewingStandBlock.HAS_BOTTLE[2], true), 3);
        level.setBlock(new BlockPos(bx + 5, fy, bz + 1), Blocks.BARREL.defaultBlockState(), 3);
        level.setBlock(new BlockPos(bx + 5, fy, bz + 5), Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
        level.setBlock(new BlockPos(bx + 4, fy, bz + 5), Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
        level.setBlock(new BlockPos(bx + 3, fy + 3, bz + 3), Blocks.LANTERN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 3);
        level.setBlock(new BlockPos(bx + 3, fy + 4, bz + 3), Blocks.OAK_FENCE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(bx + 1, fy, bz + 1), Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(bx + 2, fy, bz + 3), Blocks.RED_CARPET.defaultBlockState(), 3);
        level.setBlock(new BlockPos(bx + 3, fy, bz + 3), Blocks.RED_CARPET.defaultBlockState(), 3);
        level.setBlock(new BlockPos(bx + 2, fy, bz + 4), Blocks.RED_CARPET.defaultBlockState(), 3);
        level.setBlock(new BlockPos(bx + 3, fy, bz + 4), Blocks.RED_CARPET.defaultBlockState(), 3);
        // ground around the tower
        level.setBlock(new BlockPos(bx - 1, y, bz + 1), Blocks.TORCH.defaultBlockState(), 2);
    }

    /** Point inside the upper room (relative to the room's north-west inner corner at floor level). */
    public Vec3 room(double dx, double dy, double dz) {
        return new Vec3(c.getX() + TOWER_X - 3 + 1 + dx, y + FLOOR + dy, c.getZ() + TOWER_Z - 3 + 1 + dz);
    }
}
