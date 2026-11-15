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
}
