package dev.star1mp.giantrefresh.cinema;

import dev.star1mp.giantrefresh.entity.GiantEntity;
import dev.star1mp.giantrefresh.registry.GRContent;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityTypes;

/** The trailer's shots, by name. */
public final class Shots {
    private Shots() {}

    public static Shot create(String name) {
        Supplier<Shot> s = switch (name) {
            case "test" -> Test::new;
            case "scout" -> Scout::new;
            case "sites" -> Sites::new;
            default -> throw new IllegalArgumentException("unknown shot " + name);
        };
        return s.get();
    }

    static void roar(Shot s, Actor giant) {
        GiantEntity g = (GiantEntity) giant.e;
        s.level.playSound(null, g.getX(), g.getY() + 9, g.getZ(), GRContent.GIANT_ROAR.get(), g.getSoundSource(), 4.0F, 0.9F);
        s.level.broadcastEntityEvent(g, GiantEntity.EVENT_ROAR);
    }

    /** Pipeline test: walking, crouching, looking, a walking and roaring Giant, a moving camera. */
    static final class Test extends Shot {
        Test() {
            siteX = 616;
            siteZ = 7;
            duration = 8.0;
            dayTime = 4000;
        }

        @Override
        protected void build() {
            Actor steve = person("steve", g(0, 0), -90);
            Actor giant = giant(g(-6, 18), -90, 14, true);
            Actor villager = spawn(EntityTypes.VILLAGER, g(3, 4), 200);
            villager.lookAt(giant.e, 9);
            on(0.5, () -> steve.walkTo(g(6, 0), 0.6));
            on(0.0, () -> giant.walkTo(g(12, 18), 1.0));
            on(3.5, () -> steve.crouch(true));
            on(4.5, () -> steve.crouch(false).lookAt(() -> cam.pose(5).pos()));
            on(5.0, () -> roar(this, giant));
            on(6.5, () -> giantOf(giant).startAttack(GiantEntity.ATTACK_STOMP));
            cam.at(0, o(-5, 1.7, -5), track(steve, 1.5)).fov(60);
            cam.at(3, o(2, 1.2, -6), track(steve, 1.5)).fov(60);
            cam.at(5, o(4, 2.0, -7), track(giant, 8)).fov(55);
            cam.at(8, o(5, 3.0, -9), track(giant, 8)).fov(50);
        }
    }

    /** Places the village templates in a row and looks at them from above (layout study). */
    static final class Scout extends Shot {
        static final String[] T = {"plains_small_house_1", "plains_small_house_3", "plains_small_house_5", "plains_small_house_7",
                "plains_medium_house_1", "plains_medium_house_2", "plains_big_house_1", "plains_small_farm_1", "plains_large_farm_1",
                "plains_butcher_shop_1", "plains_library_1", "plains_animal_pen_1"};

        Scout() {
            siteX = 616;
            siteZ = 7;
            duration = 0.5;
        }

        @Override
        protected void build() {
            int y = origin.getY();
            Sets.flatten(level, origin.getX() - 70, origin.getZ() - 40, origin.getX() + 70, origin.getZ() + 40, y, 30);
            for (int i = 0; i < T.length; i++) {
                int col = i % 6;
                int row = i / 6;
                Sets.template(level, "minecraft:village/plains/houses/" + T[i],
                        b(-66 + col * 22, 0, -30 + row * 30), net.minecraft.world.level.block.Rotation.NONE);
            }
            Sets.template(level, "minecraft:village/plains/town_centers/plains_fountain_01", b(-10, 0, 20), net.minecraft.world.level.block.Rotation.NONE);
            Sets.template(level, "minecraft:village/plains/town_centers/plains_meeting_point_1", b(20, 0, 20), net.minecraft.world.level.block.Rotation.NONE);
            cam.at(0, o(0, 95, 5), 0.0F, 89.9F).fov(80);
        }
    }

    /** Looks down on candidate places for the big set, one after another. */
    static final class Sites extends Shot {
        static final int[][] P = {{-116, -173}, {64, -400}, {1185, -313}, {2857, 868}, {-600, 300}, {300, 1200}, {-1500, -200}, {1500, 900}, {-200, 1600}, {900, -1300}};

        Sites() {
            siteX = 0;
            siteZ = 0;
            duration = P.length * 2.0;
        }

        @Override
        protected void build() {
            for (int i = 0; i < P.length; i++) {
                int[] p = P[i];
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p[0], p[1]);
                var biome = level.getBiome(new net.minecraft.core.BlockPos(p[0], y, p[1])).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
                dev.star1mp.giantrefresh.GiantRefresh.LOGGER.info("[CINEMA] site {} = {} {} y {} {}", i, p[0], p[1], y, biome);
                net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(p[0] + 0.5, y + 140, p[1] + 0.5);
                cam.at(i * 2.0, at, 0.0F, 89.9F).fov(90).linear();
                cam.at(i * 2.0 + 1.99, at, 0.0F, 89.9F).fov(90).linear();
            }
        }
    }
}
