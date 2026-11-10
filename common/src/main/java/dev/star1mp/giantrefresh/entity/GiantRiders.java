package dev.star1mp.giantrefresh.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/** Crews that ride on the Giant's harness hat, and the small hordes that follow it. */
public final class GiantRiders {
    private GiantRiders() {}

    /** Weighted crew presets. */
    private enum Crew {
        ZOMBIES(30, EntityTypes.ZOMBIE, EntityTypes.ZOMBIE),
        ZOMBIE_SKELETON(25, EntityTypes.ZOMBIE, EntityTypes.SKELETON),
        SKELETONS(15, EntityTypes.SKELETON, EntityTypes.SKELETON),
        HUSK_SKELETON(10, EntityTypes.HUSK, EntityTypes.SKELETON),
        DROWNED_ZOMBIE(8, EntityTypes.DROWNED, EntityTypes.ZOMBIE),
        WITCH_SKELETON(7, EntityTypes.WITCH, EntityTypes.SKELETON),
        WAR_PARTY(5, EntityTypes.SKELETON, EntityTypes.WITCH, EntityTypes.HUSK);

        final int weight;
        final List<EntityType<? extends Mob>> members;

        @SafeVarargs
        Crew(int weight, EntityType<? extends Mob>... members) {
            this.weight = weight;
            this.members = List.of(members);
        }

        static Crew pick(RandomSource random) {
            int total = 0;
            for (Crew c : values()) {
                total += c.weight;
            }
            int roll = random.nextInt(total);
            for (Crew c : values()) {
                roll -= c.weight;
                if (roll < 0) {
                    return c;
                }
            }
            return ZOMBIES;
        }
    }

    public static void spawnRiders(GiantEntity giant, ServerLevelAccessor level, DifficultyInstance difficulty) {
        Crew crew = Crew.pick(level.getRandom());
        int slot = 0;
        for (EntityType<? extends Mob> type : crew.members) {
            Mob rider = type.create(level.getLevel(), EntitySpawnReason.JOCKEY);
            if (rider == null) {
                continue;
            }
            Vec3 pos = giant.riderPosition(slot++, 1.0F);
            rider.snapTo(pos.x, pos.y, pos.z, giant.getYRot(), 0.0F);
            rider.finalizeSpawn(level, difficulty, EntitySpawnReason.JOCKEY, null);
            cleanUp(rider);
            equipCrewHelmet(giant, rider);
            if (rider instanceof Drowned) {
                rider.setItemSlot(EquipmentSlot.MAINHAND, GiantEntity.tridentStack());
            }
            if (!rider.startRiding(giant)) {
                rider.discard();
            }
        }
    }

    /**
     * Crew members that burn in daylight get a leather cap in the colour of the hat. Without it
     * they would catch fire the first morning — or at once on a Giant found sleeping in the day.
     */
    private static void equipCrewHelmet(GiantEntity giant, Mob rider) {
        if (!(rider instanceof Zombie || rider instanceof AbstractSkeleton) || !rider.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            return;
        }
        ItemStack cap = new ItemStack(Items.LEATHER_HELMET);
        if (giant.hasHat()) {
            cap.set(DataComponents.DYED_COLOR, new DyedItemColor(DyeColor.byId(giant.getHatColor()).getTextureDiffuseColor()));
        }
        cap.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        rider.setItemSlot(EquipmentSlot.HEAD, cap);
        rider.setDropChance(EquipmentSlot.HEAD, 0.0F);
    }

    /** Zombie finalizeSpawn may roll a baby or a chicken jockey — riders must be plain adults. */
    private static void cleanUp(Mob rider) {
        if (rider instanceof Zombie zombie) {
            zombie.setBaby(false);
        }
        if (rider.isPassenger()) {
            Entity vehicle = rider.getVehicle();
            rider.stopRiding();
            if (vehicle != null) {
                vehicle.discard();
            }
        }
        for (Entity passenger : new ArrayList<>(rider.getPassengers())) {
            passenger.stopRiding();
            passenger.discard();
        }
    }

    public static void spawnHorde(GiantEntity giant, ServerLevelAccessor level, DifficultyInstance difficulty) {
        RandomSource random = level.getRandom();
        int count = 2 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            Zombie zombie = EntityTypes.ZOMBIE.create(level.getLevel(), EntitySpawnReason.REINFORCEMENT);
            if (zombie == null) {
                continue;
            }
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 4.0 + random.nextDouble() * 4.0;
            double x = giant.getX() + Math.cos(angle) * dist;
            double z = giant.getZ() + Math.sin(angle) * dist;
            double y = GiantEntity.groundY(level.getLevel(), x, z);
            if (Math.abs(y - giant.getY()) > 6) {
                y = giant.getY();
            }
            zombie.snapTo(x, y, z, random.nextFloat() * 360.0F, 0.0F);
            zombie.finalizeSpawn(level, difficulty, EntitySpawnReason.REINFORCEMENT, null);
            cleanUp(zombie);
            level.addFreshEntityWithPassengers(zombie);
        }
    }

    /** Server, every 10 ticks: melee riders jump down when their target is close to the Giant. */
    public static void tickRiders(GiantEntity giant, ServerLevel level) {
        for (Entity passenger : new ArrayList<>(giant.getPassengers())) {
            if (passenger instanceof Spider spider && spider.isAlive()) {
                tickSpiderRider(giant, spider);
                continue;
            }
            if (!(passenger instanceof Zombie zombie) || !zombie.isAlive()) {
                continue;
            }
            if (zombie instanceof Drowned && zombie.getMainHandItem().is(Items.TRIDENT)) {
                continue; // throws its trident from above instead
            }
            LivingEntity target = zombie.getTarget();
            if (target == null || !target.isAlive()) {
                continue;
            }
            double dx = target.getX() - giant.getX();
            double dz = target.getZ() - giant.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal > 10.0 || target.getY() > giant.getY() + 5.0) {
                continue;
            }
            double nx = horizontal > 0.01 ? dx / horizontal : 1.0;
            double nz = horizontal > 0.01 ? dz / horizontal : 0.0;
            double x = giant.getX() + nx * 2.8;
            double z = giant.getZ() + nz * 2.8;
            double y = GiantEntity.groundY(level, x, z);
            zombie.stopRiding();
            zombie.snapTo(x, y, z, zombie.getYRot(), 0.0F);
            zombie.resetFallDistance();
            zombie.playSound(net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT, 1.0F, 1.2F);
        }
    }

    /** A spider sitting on the head leaps down onto a target that comes close, floating down on its silk. */
    private static void tickSpiderRider(GiantEntity giant, Spider spider) {
        LivingEntity target = spider.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        double dx = target.getX() - giant.getX();
        double dz = target.getZ() - giant.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal > 14.0 || target.getY() > giant.getY() + 6.0) {
            return;
        }
        double speed = Mth.clamp((horizontal - 1.5) / 11.0, 0.15, 0.9);
        double nx = horizontal > 0.01 ? dx / horizontal : 1.0;
        double nz = horizontal > 0.01 ? dz / horizontal : 0.0;
        spider.stopRiding();
        spider.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
        spider.setDeltaMovement(nx * speed, 0.45, nz * speed);
        spider.resetFallDistance();
        spider.playSound(SoundEvents.SPIDER_AMBIENT, 1.0F, 1.3F);
    }

    public static void makePersistent(GiantEntity giant) {
        giant.setPersistenceRequired();
        for (Entity passenger : giant.getPassengers()) {
            if (passenger instanceof Mob mob) {
                mob.setPersistenceRequired();
            }
        }
    }
}
