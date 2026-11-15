package dev.star1mp.giantrefresh.cinema;

import dev.star1mp.giantrefresh.entity.GiantEntity;
import dev.star1mp.giantrefresh.entity.anim.GiantPose;
import dev.star1mp.giantrefresh.registry.GRContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

/**
 * "Footsteps" - the Giant Refresh cinematic trailer, shot by shot.
 * All coordinates are relative to the village centre (see {@link World}); +x east, +z south.
 * Minecraft yaw: 0 = facing south (+z), 90 = west, 180 = north, -90 = east.
 */
public final class Trailer {
    private Trailer() {}

    public static final String[] ORDER = {"s01_dawn", "s02_farmer", "s03_thump", "s04_lookup", "s05_footprint", "s06_follow",
            "s07_ruins", "s08_silhouette", "s09_sneak", "s10_sleeper", "s11_spider", "s12_wake", "s13_stomp", "s14_arrows",
            "s15_grab", "s16_golem", "s17_hide", "s18_growth", "s19_shrink", "s20_fall", "s21_trophy", "s22_eye"};

    public static Shot create(String name) {
        Supplier<Shot> s = switch (name) {
            case "s01_dawn" -> S01::new;
            case "s02_farmer" -> S02::new;
            case "s03_thump" -> S03::new;
            case "s04_lookup" -> S04::new;
            case "s05_footprint" -> S05::new;
            case "s06_follow" -> S06::new;
            case "s07_ruins" -> S07::new;
            case "s08_silhouette" -> S08::new;
            case "s09_sneak" -> S09::new;
            case "s10_sleeper" -> S10::new;
            case "s11_spider" -> S11::new;
            case "s12_wake" -> S12::new;
            case "s13_stomp" -> S13::new;
            case "s14_arrows" -> S14::new;
            case "s15_grab" -> S15::new;
            case "s16_golem" -> S16::new;
            case "s17_hide" -> S17::new;
            case "s18_growth" -> S18::new;
            case "s19_shrink" -> S19::new;
            case "s20_fall" -> S20::new;
            case "s21_trophy" -> S21::new;
            case "s22_eye" -> S22::new;
            default -> null;
        };
        return s == null ? null : s.get();
    }

    // ================================================================== shared
    abstract static class T extends Shot {
        World w;

        T(double duration, long time) {
            siteX = -130;
            siteZ = -190;
            this.duration = duration;
            this.dayTime = time;
        }

        @Override
        protected final void build() {
            w = new World(level, origin);
            w.build();
            scene();
        }

        abstract void scene();

        /** Ground point relative to the village centre. */
        Vec3 at(double dx, double dz) { return w.at(dx, dz); }
        Vec3 at(double dx, double dy, double dz) { return w.at(dx, dz).add(0, dy, 0); }

        Actor steve(Vec3 p, float yaw) { return person("steve", p, yaw); }

        Actor villager(String prof, Vec3 p, float yaw) {
            Actor a = summon("minecraft:villager", p, yaw, "VillagerData:{profession:\"minecraft:" + prof + "\",type:\"minecraft:plains\",level:2},NoAI:1b");
            return a;
        }

        Actor chicken(Vec3 p, float yaw) { return summon("minecraft:chicken", p, yaw, ""); }

        /** The Giant of the story: harness hat (red) with two skeletons on it. */
        Actor hero(Vec3 p, float yaw, boolean riders) {
            Actor g = giant(p, yaw, 14, false);
            if (riders) {
                for (int i = 0; i < 2; i++) {
                    Actor sk = summon("minecraft:skeleton", p.add(0, 13, 0), yaw, "");
                    if (sk != null) {
                        sk.living().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                        sk.living().setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
                        sk.e.startRiding(g.e, true, true);
                    }
                }
            }
            return g;
        }

        List<Entity> riders(Actor g) { return new ArrayList<>(g.e.getPassengers()); }

        ItemStack potion(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> p) { return PotionContents.createItemStack(Items.POTION, p); }

        ItemStack growthPotion() { return potion(BuiltInRegistries.POTION.wrapAsHolder(GRContent.GROWTH_POTION.get())); }

        ItemStack shrinkPotion() { return potion(BuiltInRegistries.POTION.wrapAsHolder(GRContent.SHRINKING_POTION.get())); }

        /** Distant heavy footfall: sound, a jolt and dust around a point. */
        void thump(double sec, Vec3 soundAt, double jolt, Vec3 dustAt, int dust) {
            jolt(sec, jolt);
            on(sec, () -> {
                sound(GRContent.GIANT_STEP.get(), soundAt, 6.0F, 0.7F);
                if (dustAt != null) {
                    dust(dustAt, dust, 1.5);
                }
            });
        }

        void spawnArrow(Vec3 from, Vec3 to, double speed) {
            Vec3 d = to.subtract(from).normalize().scale(speed);
            cmd(String.format(java.util.Locale.ROOT, "summon minecraft:arrow %.2f %.2f %.2f {Motion:[%.3f,%.3f,%.3f],pickup:0b}",
                    from.x, from.y, from.z, d.x, d.y, d.z));
            sound(SoundEvents.SKELETON_SHOOT, from, 1.0F, 1.0F);
        }

        DoubleFunction<Vec3> fixed(Vec3 p) { return t -> p; }

        DoubleFunction<Vec3> plus(DoubleFunction<Vec3> f, double x, double y, double z) { return t -> f.apply(t).add(x, y, z); }
    }

    // ================================================================== ACT 1 - the village at dawn
    /** Wide establishing crane over the sleeping village towards the sunrise. */
    static final class S01 extends T {
        S01() { super(7.0, 23250); }

        @Override
        void scene() {
            Actor farmer = villager("farmer", at(-14, -13), 0);
            farmer.lookAt(at(-14, 0.2, -10));
            Actor walker = villager("fisherman", at(-6, 4), -90);
            on(0.2, () -> walker.walkTo(at(6, 3.5), 0.5));
            Actor golem = spawn(EntityTypes.IRON_GOLEM, at(5, -6), -60);
            golem.face(-60);
            for (int i = 0; i < 4; i++) {
                chicken(at(-9 + i * 1.3, -9.5 + (i % 2)), i * 70);
            }
            during(0, 7, t -> cmd("time set " + (23250 + (int) (t * 45))));
            cam.at(0, at(-40, 18, -16), at(10, 2, -2)).fov(55);
            cam.at(7, at(-31, 9.5, -11), at(8, 3, -2)).fov(55);
        }
    }

    /** The farmer tends the wheat; chickens peck around. */
    static final class S02 extends T {
        S02() { super(4.5, 23600); }

        @Override
        void scene() {
            Vec3 f = at(-14, -13);
            Actor farmer = villager("farmer", f, 20);
            farmer.lookAt(f.add(0.4, 0.1, 1.6));
            during(0, 4.5, t -> farmer.lookAt(f.add(0.4 + Math.sin(t * 1.3) * 0.8, 0.1, 1.6)));
            for (int i = 0; i < 3; i++) {
                chicken(at(-11 + i * 0.9, -10 + (i % 2) * 0.7), 120 + i * 60);
            }
            cam.at(0, f.add(-3.4, 1.35, 2.8), f.add(0, 1.3, 0)).fov(50);
            cam.at(4.5, f.add(-2.5, 1.45, 2.1), f.add(0, 1.4, 0)).fov(48);
        }
    }

    /** Low on the ground with the chickens: thump... thump. */
    static final class S03 extends T {
        S03() { super(4.0, 23700); }

        @Override
        void scene() {
            Vec3 c0 = at(-9, -9);
            List<Actor> hens = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                hens.add(chicken(c0.add(-0.8 + i * 0.6, 0, (i % 2) * 0.8 - 0.3), 160 + i * 40));
            }
            villager("farmer", at(-14, -13), 40).lookAt(c0);
            Vec3 far = at(-20, 8, -110);
            thump(1.1, far, 1.2, c0.add(0, 0.1, 0), 10);
            thump(2.6, far, 1.8, c0.add(0, 0.1, 0), 18);
            on(2.6, () -> {
                for (int i = 0; i < hens.size(); i++) {
                    hens.get(i).e.setDeltaMovement((i - 1.5) * 0.12, 0.42, -0.05 * i);
                }
            });
            on(3.0, () -> forEach(hens, (i, h) -> h.walkTo(c0.add(6 - i, 0, 4 + i), 1.6)));
            cam.at(0, c0.add(-2.2, 0.35, 2.2), c0.add(0.2, 0.3, -0.2)).fov(52);
            cam.at(4, c0.add(-2.0, 0.35, 2.0), c0.add(0.2, 0.3, -0.2)).fov(48);
        }
    }

    /** Over the farmer's shoulder: he looks up to the north; far away, something huge moves. */
    static final class S04 extends T {
        S04() { super(5.0, 23800); }

        @Override
        void scene() {
            Vec3 f = at(-14, -13);
            Actor farmer = villager("farmer", f, 180);
            farmer.lookAt(f.add(0, 0.2, -3));
            on(1.0, () -> farmer.lookAt(at(-22, 14, -120)));
            Actor g = hero(at(-46, -122), -90, true);
            on(0.0, () -> g.walkTo(at(-6, -124), 1.0));
            Vec3 far = at(-20, 8, -110);
            thump(0.7, far, 1.0, null, 0);
            thump(3.2, far, 2.6, f.add(1, 0.1, 1), 14);
            cam.at(0, f.add(1.1, 1.95, 2.6), f.add(0, 1.6, -8)).fov(50);
            cam.at(2.2, f.add(1.1, 2.0, 2.5), at(-20, 9, -110)).fov(50);
            cam.at(5.0, f.add(1.0, 2.05, 2.4), at(-22, 10, -118)).fov(42);
        }
    }

    // ================================================================== ACT 2 - the trail
    /** A boot steps into a huge footprint; the crane rises to reveal the trail. */
    static final class S05 extends T {
        S05() { super(6.5, 3500); }

        @Override
        void scene() {
            double[] p = w.prints.get(1);
            Vec3 c0 = at(p[0], p[1]);
            Actor steve = steve(c0.add(1.4, 0, 2.6), 150);
            on(0.2, () -> steve.walkTo(c0.add(0.1, 0, 0.3), 0.3));
            on(2.6, () -> steve.lookAt(c0.add(0, 0, -0.6)));
            on(4.4, () -> steve.lookAt(at(w.prints.get(6)[0], 1.5, w.prints.get(6)[1])));
            cam.at(0, c0.add(-0.6, 5.5, 3.5), c0.add(0.2, 0, -0.2)).fov(60);
            cam.at(2.8, c0.add(-0.8, 6.5, 4.5), c0.add(0.1, 0, -0.5)).fov(60);
            cam.at(6.5, c0.add(-3.0, 12.5, 11.0), c0.add(0, 0, -11)).fov(60);
        }
    }

    /** Low, behind Steve as he follows the prints north. */
    static final class S06 extends T {
        S06() { super(5.5, 4000); }

        @Override
        void scene() {
            double[] a = w.prints.get(3);
            double[] b = w.prints.get(8);
            Actor steve = steve(at(a[0] + 0.8, a[1] + 2), 180);
            on(0.0, () -> steve.walkTo(at(b[0], b[1]), 0.42));
            DoubleFunction<Vec3> s = track(steve, 0);
            cam.follow(0, plus(s, 1.4, 2.2, 4.4), plus(s, -0.3, 1.2, -7)).fov(58);
            cam.follow(5.5, plus(s, 1.1, 2.0, 4.0), plus(s, -0.2, 1.3, -8)).fov(58);
        }
    }

    /** The trampled hut: Steve steps in, looks around, finds a huge tooth. */
    static final class S07 extends T {
        S07() { super(7.0, 5000); }

        @Override
        void scene() {
            Vec3 r = at(World.RUIN_X, World.RUIN_Z);
            Actor steve = steve(r.add(0.3, 0, 6.5), 180);
            ItemEntity tooth = new ItemEntity(level, r.x + 1.2, r.y + 0.1, r.z + 0.4, new ItemStack(GRContent.GIANT_TOOTH.get()), 0, 0, 0);
            tooth.setNeverPickUp();
            tooth.setUnlimitedLifetime();
            level.addFreshEntity(tooth);
            on(0.2, () -> steve.walkTo(r.add(0.3, 0, 1.6), 0.38));
            on(2.6, () -> steve.lookAt(r.add(-3, 1.2, -1)));
            on(3.5, () -> steve.lookAt(r.add(3, 1.0, -1.5)));
            on(4.3, () -> steve.lookAt(tooth, 0.1));
            on(4.6, () -> steve.crouch(true));
            on(5.4, () -> {
                tooth.discard();
                steve.living().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(GRContent.GIANT_TOOTH.get()));
                steve.living().swing(InteractionHand.MAIN_HAND, true);
            });
            on(5.9, () -> steve.crouch(false).lookAt(() -> steve.e.position().add(0.6, 0.9, -1.2)));
            cam.at(0, r.add(4.5, 2.4, 5.5), track(steve, 1.4)).fov(55);
            cam.at(7, r.add(3.5, 2.1, 3.8), track(steve, 1.0)).fov(50);
        }
    }

    /** Late afternoon: from a knoll Steve watches a Giant pass beyond the river. */
    static final class S08 extends T {
        S08() { super(7.0, 11700); }

        @Override
        void scene() {
            Vec3 s = at(-22, -60);
            Actor steve = steve(s, 90);
            steve.crouch(true);
            Actor g = hero(at(-54, -38), 180, true);
            on(0.0, () -> g.walkTo(at(-54, -96), 1.0));
            steve.lookAt(g.e, 9);
            cam.at(0, s.add(3.2, 1.3, 2.2), at(-54, 8, -50)).fov(50);
            cam.at(7, s.add(3.0, 1.4, 1.6), at(-54, 8, -60)).fov(46);
        }
    }

    // ================================================================== ACT 3 - the camp
    abstract static class Camp extends T {
        Actor giant;
        Camp(double d, long t) { super(d, t); }

        Vec3 camp(double dx, double dz) { return at(World.CAMP_X + dx, World.CAMP_Z + dz); }

        /** The sleeping Giant, spiders on it, skeletons on its hat. */
        void sleeper(boolean spiders) {
            giant = hero(camp(0, 0), 0, true);
            giantOf(giant).setGiantSleeping(true);
            if (spiders) {
                GiantEntity g = giantOf(giant);
                double reach = g.getBbWidth() / 2.0 + 0.5;
                double[][] on = {{-0.6, 2.0}, {0.5, 3.6}, {-1.3, 4.4}, {1.2, 2.6}};
                for (double[] o : on) {
                    double a = o[0];
                    Vec3 sp = camp(-Math.sin(a) * reach, Math.cos(a) * reach).add(0, o[1], 0);
                    Actor s = spawn(EntityTypes.SPIDER, sp, (float) Math.toDegrees(a) + 180.0F);
                    if (s.e instanceof Mob m) {
                        m.setNoAi(true);
                        m.setNoGravity(true);
                        m.snapTo(sp.x, sp.y, sp.z, (float) Math.toDegrees(a) + 180.0F, -60.0F);
                    }
                }
            }
        }
    }

    /** Sunset: Steve creeps through the tall grass towards the camp. */
    static final class S09 extends Camp {
        S09() { super(6.0, 11500); }

        @Override
        void scene() {
            sleeper(true);
            Actor steve = steve(camp(-2.5, 19), 180);
            steve.crouch(true);
            on(0.0, () -> steve.walkTo(camp(-2.5, 12.5), 0.42));
            DoubleFunction<Vec3> s = track(steve, 0);
            cam.follow(0, plus(s, 3.0, 0.55, 1.6), plus(s, -0.6, 0.7, -4)).fov(55);
            cam.follow(6, plus(s, 2.6, 0.6, 1.2), t -> camp(0, 0).add(0, 5, 0)).fov(52);
        }
    }

    /** The reveal: a slow push towards the sleeping Giant, spiders crawling on it. */
    static final class S10 extends Camp {
        S10() { super(7.0, 11550); }

        @Override
        void scene() {
            sleeper(true);
            Actor steve = steve(camp(-6.0, 9.5), 160);
            steve.crouch(true);
            steve.lookAt(giant.e, 6);
            cam.at(0, camp(-9.5, 13.5).add(0, 2.2, 0), camp(0, 0).add(0, 5.0, 0)).fov(55);
            cam.at(7, camp(-6.0, 9.0).add(0, 3.0, 0), camp(0, 0).add(0, 6.0, 0)).fov(50);
        }
    }

    /** A spider drops right in front of Steve; he jumps back and runs. */
    static final class S11 extends Camp {
        S11() { super(3.5, 11600); }

        @Override
        void scene() {
            sleeper(false);
            Vec3 s = camp(-3.0, 11.0);
            Actor steve = steve(s, 180);
            steve.crouch(true);
            steve.lookAt(giant.e, 6);
            on(0.7, () -> {
                Actor sp = spawn(EntityTypes.SPIDER, s.add(0.1, 3.4, -1.3), 0);
                sp.e.setDeltaMovement(0, -0.2, 0);
                sound(SoundEvents.SPIDER_AMBIENT, s.add(0, 1, -1), 1.2F, 1.1F);
            });
            on(1.15, () -> {
                steve.crouch(false).lookAt(() -> s.add(0.1, 0.6, -1.3));
                steve.e.setDeltaMovement(0, 0.35, 0.28);
            });
            on(2.0, () -> steve.sprint(true).walkTo(s.add(1.5, 0, 14), 1.0));
            cam.at(0, s.add(0.7, 1.15, -3.2), s.add(0, 1.0, 0)).fov(52);
            cam.at(3.5, s.add(0.8, 1.2, -3.0), s.add(0, 1.0, 1.5)).fov(55);
        }
    }

    /** The Giant opens its eyes, stretches, and roars. */
    static final class S12 extends Camp {
        S12() { super(6.5, 11650); }

        @Override
        void scene() {
            sleeper(false);
            on(0.8, () -> giantOf(giant).wakeUp(level, null));
            on(3.6, () -> Shots.roar(this, giant));
            jolt(3.7, 2.4);
            jolt(4.3, 1.4);
            for (Entity r : riders(giant)) {
                if (r instanceof Mob m) {
                    on(3.7, () -> m.setAggressive(true));
                }
            }
            cam.at(0, camp(1.0, 7.5).add(0, 4.0, 0), camp(0, 0).add(0, 6.0, 0)).fov(50);
            cam.at(3.2, camp(1.2, 8.5).add(0, 3.2, 0), camp(0, 0).add(0, 9.6, 0)).fov(52);
            cam.at(6.5, camp(1.4, 9.5).add(0, 2.4, 0), camp(0, 0).add(0, 10.5, 0)).fov(58);
        }
    }

    // ================================================================== ACT 4 - the chase
    /** Steve sprints at the camera; behind him the Giant stomps and the shock wave throws him. */
    static final class S13 extends Camp {
        S13() { super(4.0, 11700); }

        @Override
        void scene() {
            giant = hero(camp(0, 6), 0, true);
            Actor steve = steve(camp(0.5, 16), 0);
            steve.sprint(true).walkTo(camp(1.0, 36), 1.0);
            on(0.2, () -> giantOf(giant).startAttack(GiantEntity.ATTACK_STOMP));
            double impact = 0.2 + 26 / 20.0;
            jolt(impact, 3.0);
            on(impact, () -> {
                steve.stop();
                steve.e.setDeltaMovement(0.05, 0.62, 0.75);
                dust(steve.e.position(), 30, 2.0);
            });
            DoubleFunction<Vec3> s = track(steve, 0);
            cam.at(0, camp(2.5, 30).add(0, 0.9, 0), plus(s, 0, 2.5, 0)).fov(72);
            cam.at(4, camp(2.2, 31).add(0, 0.8, 0), plus(s, 0, 2.0, 0)).fov(72);
        }
    }

    /** From the Giant's hat: the skeletons shoot down at the tiny running Steve. */
    static final class S14 extends T {
        S14() { super(3.5, 11750); }

        @Override
        void scene() {
            Actor g = hero(at(0, -78), 0, true);
            on(0.0, () -> g.walkTo(at(0, -40), 1.0));
            Actor steve = steve(at(1.5, -64), 0);
            steve.sprint(true).walkTo(at(-2, -30), 1.0);
            List<Entity> rs = riders(g);
            for (Entity r : rs) {
                if (r instanceof Mob m) {
                    m.setAggressive(true);
                }
                Actor a = actors.stream().filter(x -> x.e == r).findFirst().orElse(null);
                if (a != null) {
                    a.lookAt(steve.e, 1);
                }
            }
            for (double s : new double[] {0.5, 1.2, 1.9, 2.6}) {
                int idx = (int) (s * 10) % Math.max(1, rs.size());
                on(s, () -> {
                    if (!rs.isEmpty()) {
                        Entity r = rs.get(idx);
                        spawnArrow(r.getEyePosition(), steve.e.position().add((idx - 0.5) * 1.5, 0, 2.5), 1.6);
                    }
                });
            }
            DoubleFunction<Vec3> gh = track(g, 0);
            DoubleFunction<Vec3> s = track(steve, 0);
            cam.follow(0, plus(gh, -2.6, 14.6, -2.6), plus(s, 0, 0.5, 0)).fov(62);
            cam.follow(3.5, plus(gh, -2.4, 14.4, -2.4), plus(s, 0, 0.5, 0)).fov(58);
        }
    }

    /** The Giant reaches down and snatches Steve. */
    static final class S15 extends T {
        S15() { super(3.4, 11800); }

        @Override
        void scene() {
            Actor g = hero(at(0, -52), 0, true);
            Actor steve = steve(at(0.3, -48.5), 0);
            steve.sprint(true).walkTo(at(1, -30), 1.0);
            on(0.0, () -> g.walkTo(at(0.3, -44), 1.0));
            on(0.5, () -> {
                g.stop();
                giantOf(g).setTarget(steve.living());
                giantOf(g).startAttack(GiantEntity.ATTACK_GRAB);
            });
            during(0.5, 3.4, t -> {
                GiantEntity gi = giantOf(g);
                if (gi.getHeld() == null && gi.getAttackTicks() > GiantPose.GRAB_CATCH) {
                    gi.showcaseGrab(steve.living(), GiantEntity.ATTACK_GRAB, gi.getAttackTicks());
                    steve.stop();
                }
            });
            DoubleFunction<Vec3> s = track(steve, 0);
            cam.at(0, at(9, 1.2, -40), plus(s, 0, 1.5, 0)).fov(62);
            cam.at(3.4, at(9.5, 1.0, -41), plus(s, 0, 0.8, 0)).fov(62);
        }
    }

    /** The village's iron golem charges in and slams the Giant; the Giant flings Steve away. */
    static final class S16 extends T {
        S16() { super(4.0, 11850); }

        @Override
        void scene() {
            Actor g = hero(at(0, -44), 0, true);
            Actor steve = steve(at(0, -40), 0);
            on(0.0, () -> giantOf(g).showcaseGrab(steve.living(), GiantEntity.ATTACK_GRAB, 22));
            Actor golem = spawn(EntityTypes.IRON_GOLEM, at(9, -33), 130);
            golem.walkTo(at(2.2, -41.5), 1.2);
            on(0.75, () -> {
                level.broadcastEntityEvent(golem.e, (byte) 4);
                sound(SoundEvents.IRON_GOLEM_ATTACK, golem.e.position(), 1.5F, 1.0F);
            });
            on(0.85, () -> giantOf(g).hurtServer(level, level.damageSources().mobAttack((LivingEntity) golem.e), 4.0F));
            jolt(0.85, 1.0);
            golem.lookAt(g.e, 5);
            cam.at(0, at(13, 1.6, -30), at(3, 6, -42)).fov(62);
            cam.at(4, at(12, 1.8, -31), at(1, 7, -42)).fov(62);
        }
    }

    /** In the square: villagers run inside and slam their doors. */
    static final class S17 extends T {
        S17() { super(4.0, 11900); }

        @Override
        void scene() {
            Actor g = hero(at(2, -46), 0, true);
            on(0.0, () -> g.walkTo(at(2, -36), 1.0));
            String[] prof = {"librarian", "farmer", "cleric", "fletcher"};
            Vec3[] start = {at(-3, 2), at(3, -3), at(4, 4), at(-4, -4)};
            for (int i = 0; i < start.length; i++) {
                Actor v = villager(prof[i], start[i], 0);
                BlockPos door = nearestDoor(start[i]);
                if (door != null) {
                    Vec3 d = new Vec3(door.getX() + 0.5, door.getY(), door.getZ() + 0.5);
                    double delay = i * 0.25;
                    on(delay, () -> {
                        door(door, true);
                        v.walkTo(d, 0.95);
                    });
                    on(2.6 + i * 0.25, () -> {
                        door(door, false);
                        v.e.discard();
                    });
                }
            }
            cam.at(0, at(-1, 2.6, 9), at(1, 5, -22)).fov(62);
            cam.at(4, at(-0.5, 2.4, 8), at(1, 6, -26)).fov(60);
        }

        BlockPos nearestDoor(Vec3 from) {
            BlockPos best = null;
            double bd = 1e9;
            for (BlockPos d : w.doors) {
                double dd = d.distToCenterSqr(from);
                if (dd < bd) {
                    bd = dd;
                    best = d;
                }
            }
            return best;
        }
    }

    /** Steve drinks the Growth potion and grows to five blocks - the Giant is still twelve. */
    static final class S18 extends T {
        S18() { super(7.0, 11950); }

        @Override
        void scene() {
            Actor g = hero(at(0, -40), 0, true);
            Actor steve = steve(at(0, -33), 180);
            steve.living().setItemSlot(EquipmentSlot.MAINHAND, growthPotion());
            g.lookAt(steve.e, 1.2);
            steve.lookAt(g.e, 10);
            on(0.4, () -> steve.living().startUsingItem(InteractionHand.MAIN_HAND));
            on(2.0, () -> {
                steve.living().stopUsingItem();
                steve.living().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GLASS_BOTTLE));
                steve.living().addEffect(new MobEffectInstance(GRContent.growthHolder(), -1, 0, false, false));
            });
            on(5.4, () -> g.lookAt(() -> steve.e.position().add(0.9, 3.2, 0)));
            cam.at(0, at(11, 2.5, -36), at(0, 3.5, -36.5)).fov(55);
            cam.at(4.5, at(13, 4.5, -36), at(0, 6.0, -36.5)).fov(60);
            cam.at(7, at(13.5, 5.0, -36), at(0, 6.5, -36.5)).fov(60);
        }
    }

    /** "Plan B": Shrinking. Tiny Steve dashes between the Giant's legs; the stomp misses. */
    static final class S19 extends T {
        S19() { super(6.5, 12000); }

        @Override
        void scene() {
            Actor g = hero(at(0, -40), 0, true);
            Actor steve = steve(at(0, -31), 180);
            steve.living().addEffect(new MobEffectInstance(GRContent.growthHolder(), -1, 0, false, false));
            steve.living().setItemSlot(EquipmentSlot.MAINHAND, shrinkPotion());
            g.lookAt(steve.e, 1.0);
            steve.lookAt(g.e, 10);
            on(0.3, () -> steve.living().startUsingItem(InteractionHand.MAIN_HAND));
            on(1.9, () -> {
                steve.living().stopUsingItem();
                steve.living().setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                steve.living().removeEffect(GRContent.growthHolder());
                steve.living().addEffect(new MobEffectInstance(GRContent.shrinkingHolder(), -1, 0, false, false));
            });
            on(3.6, () -> steve.sprint(true).walkTo(at(0.2, -50), 1.0));
            on(3.7, () -> giantOf(g).startAttack(GiantEntity.ATTACK_STOMP));
            jolt(3.7 + 1.3, 2.2);
            cam.at(0, at(1.2, 0.5, -50), track(steve, 0.6)).fov(62);
            cam.at(6.5, at(1.0, 0.45, -52), track(steve, 0.4)).fov(62);
        }
    }

    /** The golem and Steve bring the Giant down - it topples in slow motion. */
    static final class S20 extends T {
        S20() {
            super(4.0, 12050);
            timeScale = 0.55;
        }

        @Override
        void scene() {
            Actor g = hero(at(0, -42), 0, true);
            Actor golem = spawn(EntityTypes.IRON_GOLEM, at(2.4, -39.5), 160);
            golem.lookAt(g.e, 5);
            Actor steve = steve(at(-2.5, -39.0), 200);
            steve.lookAt(g.e, 3);
            on(0.3, () -> level.broadcastEntityEvent(golem.e, (byte) 4));
            on(0.4, () -> steve.living().swing(InteractionHand.MAIN_HAND, true));
            on(0.5, () -> giantOf(g).kill(level));
            on(0.5 + 26 / 20.0, () -> dust(g.e.position().add(4, 0.2, 0), 60, 4.0));
            jolt(0.5 + 26 / 20.0, 3.2);
            cam.at(0, at(-12, 1.3, -30), at(0, 6, -42)).fov(60);
            cam.at(4, at(-13, 1.6, -31), at(1, 3, -42)).fov(62);
        }
    }

    // ================================================================== ACT 5 - epilogue
    /** Next morning in Steve's tower: the Giant's head on display. */
    static final class S21 extends T {
        S21() { super(6.5, 1500); }

        @Override
        void scene() {
            Actor steve = steve(w.room(2.5, 0, 3.0), 180);
            steve.living().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(GRContent.GIANT_TOOTH.get()));
            steve.lookAt(w.room(1.5, 0.8, 0.4));
            on(4.2, () -> steve.lookAt(() -> steve.e.position().add(0.5, 0.9, -1.2)));
            cam.at(0, w.room(4.6, 1.5, 4.4), w.room(1.6, 0.8, 0.5)).fov(62);
            cam.at(6.5, w.room(4.2, 1.6, 3.6), w.room(2.0, 1.1, 1.0)).fov(58);
        }
    }

    /** The floor trembles. In the window: an enormous eye. */
    static final class S22 extends T {
        S22() { super(7.0, 1600); }

        @Override
        void scene() {
            Actor steve = steve(w.room(2.4, 0, 2.2), 180);
            steve.lookAt(w.room(1.5, 0.8, 0.4));
            Vec3 win = w.room(5.5, 0, 2.5);
            Actor g = giant(new Vec3(win.x + 2.6, w.y, win.z - 14), 0, 4, false);
            on(0.2, () -> g.walkTo(new Vec3(win.x + 2.6, w.y, win.z), 1.0));
            during(3.6, 7, t -> g.face(90).lookAt(steve.e, 1.5));
            thump(1.0, win.add(4, -6, -8), 1.4, null, 0);
            thump(2.2, win.add(4, -6, -3), 2.0, null, 0);
            on(2.5, () -> steve.lookAt(win.add(0, 1.9, 0)));
            on(4.8, () -> steve.backUp(0.18f));
            on(5.6, () -> steve.stop());
            cam.at(0, w.room(0.4, 1.7, 3.6), w.room(2.0, 1.4, 1.0)).fov(62);
            cam.at(2.4, w.room(0.4, 1.7, 3.4), win.add(0, 1.8, 0)).fov(62);
            cam.at(7, w.room(0.6, 1.65, 3.0), win.add(0, 2.0, 0)).fov(50);
        }
    }
}
