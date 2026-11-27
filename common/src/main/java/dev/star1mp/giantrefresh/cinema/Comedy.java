package dev.star1mp.giantrefresh.cinema;

import dev.star1mp.giantrefresh.entity.GiantEntity;
import dev.star1mp.giantrefresh.registry.GRContent;
import java.util.function.DoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** Three separate comedy sets, recorded by the Minecraft client. */
final class Comedy extends Trailer.T {
    private final String story;
    private Vec3 center;

    Comedy(String story) {
        super(18, switch (story) {
            case "gate" -> 3750;
            case "hatch" -> 6800;
            default -> 11100;
        });
        this.story = story;
    }

    private Vec3 point(double x, double y, double z) {
        return center.add(x, y, z);
    }

    private void block(double x, double y, double z, BlockState state) {
        level.setBlockAndUpdate(BlockPos.containing(point(x, y, z)), state);
    }

    private void hold(Actor actor, ItemStack stack) {
        actor.living().setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

    private void wave(Actor actor) {
        actor.living().swing(InteractionHand.MAIN_HAND);
    }

    private void frame(double start, double end, Vec3 eye, Vec3 look, float lens) {
        frame(start, end, eye, fixed(look), lens);
    }

    private void frame(double start, double end, Vec3 eye, DoubleFunction<Vec3> look, float lens) {
        cam.at(start, eye, look).fov(lens);
        cam.at(end - .002, eye, look).fov(lens);
    }

    private void mark(String event) {
        dev.star1mp.giantrefresh.GiantRefresh.LOGGER.info("[COMEDY] {} {}", story, event);
    }

    @Override
    void scene() {
        cmd("difficulty normal");
        center = at(7, -32);
        var base = BlockPos.containing(center);
        Sets.flatten(level, base.getX() - 12, base.getZ() - 12,
                base.getX() + 12, base.getZ() + 16, base.getY(), 16);
        switch (story) {
            case "gate" -> gate();
            case "hatch" -> hatch();
            case "bell" -> bell();
            default -> throw new IllegalArgumentException(story);
        }
    }

    private void gate() {
        for (int x = -6; x <= 6; x++) {
            if (x != 2 && x != 4) block(x, 0, 4, Blocks.OAK_FENCE.defaultBlockState());
            for (int z = 5; z <= 8; z++) {
                if ((x + z) % 3 != 0) block(x, -1, z, Blocks.DIRT_PATH.defaultBlockState());
            }
        }
        BlockState gate = Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH);
        block(4, 0, 4, gate);
        block(6, 0, 5, Blocks.BARREL.defaultBlockState());
        Actor steve = steve(point(2.95, 0, 5.65), 139);
        Actor alex = person("alex", point(5.23, 0, 6.42), 136);
        Actor carrier = giant(point(-1.30, 0, -3.2), 0, -1, false);
        carrier.face(0);
        hold(steve, new ItemStack(Items.OAK_FENCE));
        alex.lookAt(steve.e, 1.35);
        steve.lookAt(point(2, .75, 4));
        on(.30, () -> {
            wave(steve);
            block(2, 0, 4, Blocks.OAK_FENCE.defaultBlockState());
            hold(steve, ItemStack.EMPTY);
        });
        on(.75, () -> steve.lookAt(alex.e, 1.5));
        on(3.20, () -> {
            carrier.walkTo(point(-1.30, 0, 7.0), 1.38);
            alex.lookAt(carrier.e, 9.5);
        });
        on(4.52, () -> {
            steve.lookAt(carrier.e, 9.5).backUp(.35f);
        });
        on(5.20, () -> steve.stop());
        on(6.15, () -> alex.lookAt(point(-1.2, .30, 4)));
        on(8.80, () -> {
            steve.lookAt(point(-1.2, .5, 4));
            alex.lookAt(steve.e, 1.45);
        });
        on(9.25, () -> mark("wall gap: " + level.getBlockState(BlockPos.containing(point(-1, 0, 4))).isAir()));
        on(10.30, () -> {
            steve.lookAt(point(4, .9, 4));
            steve.walkTo(point(4, 0, 5.25), .52);
        });
        on(12.25, () -> {
            wave(steve);
            block(4, 0, 4, gate.setValue(FenceGateBlock.OPEN, true));
            steve.walkTo(point(4, 0, 2.5), .57);
        });
        on(13.55, () -> {
            alex.lookAt(steve.e, 1.14);
            alex.crouch(true);
        });
        frame(0, 2.72, point(5.62, 1.96, 7.72), point(3.07, 1.04, 5.22), 63);
        frame(2.72, 8.9, point(5.75, 3.65, 13.85), point(.75, 3.70, 3.6), 66);
        frame(8.9, 11.18, point(6.25, 1.50, 8.7), point(3.75, 1.24, 5.63), 64);
        frame(11.18, 15.4, point(7.6, 3.6, 8.8), point(1.4, 1.05, 4.6), 69);
        frame(15.4, 18.05, point(8.2, 3.8, 12.3), point(.95, 2.2, 5.05), 64);
    }

    private void hatch() {
        for (int x = -4; x <= 4; x++) {
            for (int z = -5; z <= 1; z++) block(x, -1, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
            for (int y = 0; y <= 2; y++) {
                if ((x == -1 && y == 0) || (x == 2 && y < 2)) continue;
                block(x, y, 0, (x == -4 || x == 4 ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.SPRUCE_PLANKS).defaultBlockState());
            }
            block(x, 3, 0, Blocks.SPRUCE_SLAB.defaultBlockState());
            block(x, 0, -5, Blocks.OAK_FENCE.defaultBlockState());
        }
        block(-1, 1, 0, Blocks.GLASS.defaultBlockState());
        Vec3 door = point(2, 0, 0);
        BlockPos doorLower = BlockPos.containing(door);
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        level.setBlock(doorLower, lower, 2);
        level.setBlockAndUpdate(doorLower.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

        Actor steve = steve(point(-1, 0, 2.0), 180);
        Actor alex = person("alex", point(2, 0, 2.85), 180);
        Actor hen = summon("minecraft:chicken", point(-1, 0, -1.7), 0, "NoAI:1b");
        hen.lookAt(steve.e, 1.08);
        hold(steve, shrinkPotion());
        steve.lookAt(point(-1, .43, -.12));
        alex.lookAt(steve.e, 1.55);
        on(.38, () -> steve.crouch(true));
        on(1.6, () -> {
            steve.crouch(false).lookAt(hen.e, .45);
            steve.living().startUsingItem(InteractionHand.MAIN_HAND);
        });
        on(3.22, () -> {
            steve.living().stopUsingItem();
            hold(steve, new ItemStack(Items.GLASS_BOTTLE));
            steve.living().addEffect(new MobEffectInstance(GRContent.shrinkingHolder(), -1, 0, false, false));
            mark("shrinking applied");
        });
        on(5.10, () -> steve.walkTo(point(-1, 0, .74), .55));
        on(6.26, () -> steve.stop());
        // Align the short actor with the one-block opening so the mannequin follows its floor.
        during(6.30, 7.80, t -> {
            double k = (t - 6.3) / 1.5;
            Vec3 p = point(-1, 0, .74 - 1.90 * k);
            steve.e.setPos(p.x, p.y, p.z);
        });
        on(8.20, () -> {
            steve.lookAt(hen.e, .50);
            hold(steve, new ItemStack(Items.WHEAT_SEEDS));
            hen.walkTo(point(-.55, 0, -1.0), .45);
        });
        on(9.85, () -> alex.lookAt(door.add(0, 1.10, 0)).walkTo(point(2, 0, 1.09), .68));
        on(11.02, () -> {
            wave(alex);
            door(doorLower, true);
        });
        on(11.12, () -> alex.stop());
        during(11.15, 12.15, t -> {
            double k = (t - 11.15);
            Vec3 p = point(2, 0, 1.10 - 2.95 * k);
            alex.e.setPos(p.x, p.y, p.z);
        });
        on(12.20, () -> {
            alex.lookAt(steve.e, .61);
            steve.lookAt(alex.e, 1.1);
            hen.lookAt(alex.e, 1.35);
        });
        on(13.12, () -> steve.crouch(true));
        on(14.50, () -> {
            mark("hatch passed: " + (steve.e.getZ() < door.z) + "; door used: " + (alex.e.getZ() < door.z));
        });
        frame(0, 5.10, point(-4.08, 2.1, 4.0), point(-.82, 1.03, 1.3), 62);
        frame(5.10, 8.0, point(-2.75, 1.35, 3.32), point(-.99, .57, .73), 59);
        frame(8.0, 10.56, point(-4.45, 1.28, -2.85), point(-.72, .54, -1.1), 58);
        frame(10.56, 15.4, point(-1.2, 1.97, -4.45), point(.65, 1.20, -.97), 65);
        frame(15.4, 18.05, point(-1.32, 2.04, -4.65), point(.6, 1.31, -1.1), 65);
    }

    private void bell() {
        Actor giant = giant(point(0, 0, -3.65), 0, -1, false);
        GiantEntity sleeper = giantOf(giant);
        sleeper.setGiantSleeping(true);
        block(2.1, 0, 3.7, Blocks.NOTE_BLOCK.defaultBlockState());
        BlockPos bell = BlockPos.containing(point(2.1, 0, 3.7));
        Actor steve = steve(point(1.28, 0, 5.48), 180);
        Actor alex = person("alex", point(4.1, 0, 5.25), 154);
        Actor creeper = summon("minecraft:creeper", point(-.55, 0, 2.65), 15, "NoAI:1b,Invulnerable:1b");
        Creeper fuse = (Creeper) creeper.e;
        steve.lookAt(point(2.1, .6, 3.7));
        alex.lookAt(steve.e, 1.38);
        creeper.lookAt(sleeper, 5.0);
        on(.42, () -> {
            wave(steve);
            level.blockEvent(bell, Blocks.NOTE_BLOCK, 0, 0);
        });
        on(1.40, () -> steve.lookAt(sleeper, 6.7));
        on(2.90, () -> alex.lookAt(sleeper, 6.8));
        on(3.55, () -> {
            steve.lookAt(point(2.1, .6, 3.7));
            wave(steve);
            level.blockEvent(bell, Blocks.NOTE_BLOCK, 0, 0);
        });
        on(4.70, () -> {
            steve.lookAt(alex.e, 1.45);
            alex.lookAt(steve.e, 1.45);
        });
        on(6.20, () -> {
            steve.sprint(true).walkTo(point(4.65, 0, 5.7), .88);
            sleeper.wakeUp(level, null);
            alex.lookAt(sleeper, 7);
        });
        on(7.95, () -> giant.walkTo(point(0, 0, 2.3), 1.34));
        on(9.52, () -> {
            mark("creeper already ignited by footfall: " + fuse.isIgnited());
            if (fuse.isAlive() && !fuse.isIgnited()) fuse.ignite();
        });
        on(9.74, () -> {
            alex.backUp(.48f);
            steve.lookAt(creeper.e, 1.15);
        });
        on(10.29, () -> alex.stop().crouch(true).lookAt(creeper.e, 1.2));
        jolt(10.97, .78);
        on(11.18, () -> {
            steve.crouch(true).lookAt(point(.2, -.30, 2.8));
            mark("creeper exploded: " + !fuse.isAlive());
        });
        on(12.12, () -> alex.crouch(false).lookAt(steve.e, 1.2));
        on(13.05, () -> {
            steve.crouch(false).lookAt(alex.e, 1.4);
            wave(steve);
        });
        frame(0, 3.18, point(4.9, 1.74, 7.15), point(1.65, 1.31, 4.32), 65);
        frame(3.18, 6.26, point(2.0, 2.56, 12.07), point(1.3, 2.90, 3.0), 65);
        frame(6.26, 9.14, point(3.97, 3.85, 12.42), point(.95, 3.82, 2.09), 66);
        frame(9.14, 11.31, point(-4.82, 1.35, 6.65), point(-.09, 1.96, 1.90), 64);
        frame(11.31, 15.4, point(7.5, 2.53, 9.3), point(3.24, 1.26, 4.74), 66);
        frame(15.4, 18.05, point(7.7, 3.35, 12.25), point(1.2, 3.06, 1.1), 66);
    }
}
