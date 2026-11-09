package dev.star1mp.giantrefresh.entity;

import dev.star1mp.giantrefresh.GiantRefresh;
import dev.star1mp.giantrefresh.config.GRConfig;
import dev.star1mp.giantrefresh.entity.ai.FollowGiantGoal;
import dev.star1mp.giantrefresh.mixin.MobAccessor;
import dev.star1mp.giantrefresh.registry.GRContent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Loader-independent event handlers; each loader forwards its own events here. */
public final class GiantEvents {
    private static final ConcurrentLinkedQueue<Entity> VANILLA_GIANTS = new ConcurrentLinkedQueue<>();

    private GiantEvents() {}

    /** An entity was added to a server level (spawned or loaded from disk). */
    public static void onEntityJoin(Entity entity, ServerLevel level) {
        GRConfig cfg = GRConfig.get();
        if (cfg.replaceVanillaGiant && entity.getType() == EntityTypes.GIANT) {
            VANILLA_GIANTS.add(entity);
            return;
        }
        if (cfg.zombiesFollowGiants && entity instanceof Mob mob && !(entity instanceof GiantEntity)) {
            if (mob instanceof Zombie zombie) {
                ((MobAccessor) zombie).giantrefresh$goalSelector().addGoal(6, new FollowGiantGoal(zombie, 4.0, 10.0));
            } else if (mob instanceof AbstractSkeleton skeleton) {
                ((MobAccessor) skeleton).giantrefresh$goalSelector().addGoal(6, new FollowGiantGoal(skeleton, 5.0, 9.0));
            }
        }
    }

    /** End of every server level tick. */
    public static void onLevelTick(ServerLevel level) {
        if (!VANILLA_GIANTS.isEmpty()) {
            List<Entity> pending = new ArrayList<>();
            Entity e;
            while ((e = VANILLA_GIANTS.poll()) != null) {
                if (e.level() == level) {
                    convertVanillaGiant(level, e);
                } else {
                    pending.add(e);
                }
            }
            VANILLA_GIANTS.addAll(pending);
        }
        tickNaturalSpawning(level);
    }

    private static void convertVanillaGiant(ServerLevel level, Entity old) {
        if (old.isRemoved() || !old.isAlive()) {
            return;
        }
        GiantEntity giant = GRContent.GIANT.get().create(level, EntitySpawnReason.CONVERSION);
        if (giant == null) {
            return;
        }
        giant.snapTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), old.getXRot());
        if (old.hasCustomName()) {
            giant.setCustomName(old.getCustomName());
        }
        boolean persistent = old instanceof Mob mob && mob.isPersistenceRequired();
        boolean noAi = old instanceof Mob mob2 && mob2.isNoAi();
        giant.finalizeSpawn(level, level.getCurrentDifficultyAt(old.blockPosition()), EntitySpawnReason.TRIGGERED, null);
        giant.setNoAi(noAi);
        if (persistent) {
            GiantRiders.makePersistent(giant);
        }
        old.discard();
        level.addFreshEntityWithPassengers(giant);
        GiantRefresh.LOGGER.debug("Replaced a vanilla Giant at {}", old.blockPosition());
    }

    private static void tickNaturalSpawning(ServerLevel level) {
        GRConfig cfg = GRConfig.get();
        if (!cfg.naturalSpawning || level.dimension() != Level.OVERWORLD || level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        if (level.getGameTime() % cfg.spawnAttemptInterval != 0) {
            return;
        }
        if (!level.getGameRules().get(GameRules.SPAWN_MONSTERS)) {
            return;
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || random.nextDouble() >= cfg.spawnChance) {
                continue;
            }
            trySpawnNear(level, player, random, cfg);
        }
    }

    private static void trySpawnNear(ServerLevel level, ServerPlayer player, RandomSource random, GRConfig cfg) {
        AABB exclusion = player.getBoundingBox().inflate(cfg.giantExclusionRadius);
        if (!level.getEntitiesOfClass(GiantEntity.class, exclusion, Entity::isAlive).isEmpty()) {
            return;
        }
        boolean night = level.isDarkOutside();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = cfg.minSpawnDistance + random.nextDouble() * (cfg.maxSpawnDistance - cfg.minSpawnDistance);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
            BlockPos probe = new BlockPos(x, player.blockPosition().getY(), z);
            if (!level.isLoaded(probe)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBiome(pos).is(GRContent.GIANT_BIOMES)) {
                continue;
            }
            BlockState below = level.getBlockState(pos.below());
            if (below.isAir() || !below.getFluidState().isEmpty() || !below.isSolid()) {
                continue;
            }
            if (!level.noCollision(GRContent.GIANT.get().getSpawnAABB(x + 0.5, y, z + 0.5))) {
                continue;
            }
            if (level.getNearestPlayer(x + 0.5, y, z + 0.5, cfg.minSpawnDistance - 8.0, false) != null) {
                continue;
            }
            GiantEntity giant = GRContent.GIANT.get().create(level, EntitySpawnReason.NATURAL);
            if (giant == null) {
                return;
            }
            giant.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
            giant.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
            if (!night) {
                giant.setGiantSleeping(true); // daytime giants are found napping
            }
            level.addFreshEntityWithPassengers(giant);
            return;
        }
    }
}
