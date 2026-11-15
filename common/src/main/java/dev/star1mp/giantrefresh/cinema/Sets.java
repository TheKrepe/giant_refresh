package dev.star1mp.giantrefresh.cinema;

import dev.star1mp.giantrefresh.GiantRefresh;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Set building for the trailer: flat ground, vanilla village buildings, trees. */
public final class Sets {
    private Sets() {}

    /** Flat grass at height y (first air block) over a rectangle; everything above is cleared. */
    public static void flatten(ServerLevel level, int x1, int z1, int x2, int z2, int y, int clear) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                for (int yy = Math.max(top, y + clear); yy >= y; yy--) {
                    m.set(x, yy, z);
                    if (!level.getBlockState(m).isAir()) {
                        level.setBlock(m, air, 2 | 16);
                    }
                }
                for (int yy = y - 1; yy >= y - 6; yy--) {
                    m.set(x, yy, z);
                    BlockState st = level.getBlockState(m);
                    BlockState want = yy == y - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    if (yy < y - 3 && !st.isAir() && st.getFluidState().isEmpty()) {
                        break;
                    }
                    level.setBlock(m, want, 2 | 16);
                }
            }
        }
    }

    public static void scatterPlants(ServerLevel level, int x1, int z1, int x2, int z2, int y, double grass, double flowers, long seed) {
        RandomSource r = RandomSource.create(seed);
        BlockState[] fl = {Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(),
                Blocks.CORNFLOWER.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState()};
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                m.set(x, y, z);
                if (!level.getBlockState(m).isAir() || !level.getBlockState(m.below()).is(Blocks.GRASS_BLOCK)) {
                    continue;
                }
                double v = r.nextDouble();
                if (v < flowers) {
                    level.setBlock(m, fl[r.nextInt(fl.length)], 2);
                } else if (v < flowers + grass) {
                    level.setBlock(m, Blocks.SHORT_GRASS.defaultBlockState(), 2);
                }
            }
        }
    }

    /** Places a vanilla structure template; returns its bounding box (or null). */
    public static BoundingBox template(ServerLevel level, String id, BlockPos at, Rotation rot) {
        var opt = level.getStructureManager().get(Identifier.parse(id));
        if (opt.isEmpty()) {
            GiantRefresh.LOGGER.warn("[CINEMA] no template {}", id);
            return null;
        }
        StructureTemplate t = opt.get();
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rot).setIgnoreEntities(true)
                .addProcessor(JigsawReplacementProcessor.INSTANCE);
        t.placeInWorld(level, at, at, settings, RandomSource.create(1), 2);
        BoundingBox box = t.getBoundingBox(settings, at);
        Vec3i size = t.getSize(rot);
        List<String> doors = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockState st = level.getBlockState(m.set(x, y, z));
                    if (st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                        doors.add((x - at.getX()) + "," + (y - at.getY()) + "," + (z - at.getZ()) + "/" + st.getValue(DoorBlock.FACING));
                    }
                }
            }
        }
        GiantRefresh.LOGGER.info("[CINEMA] template {} rot {} at {} size {} box {} doors {}", id, rot, at, size, box, doors);
        return box;
    }
}
