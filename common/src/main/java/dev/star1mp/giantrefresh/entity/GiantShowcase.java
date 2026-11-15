package dev.star1mp.giantrefresh.entity;

import dev.star1mp.giantrefresh.GiantRefresh;
import dev.star1mp.giantrefresh.config.GRConfig;
import dev.star1mp.giantrefresh.registry.GRContent;
import dev.star1mp.giantrefresh.worldgen.GiantTrailFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Promo screenshots ({@code -Dgiantrefresh.showcase=true}, dev client only): stages a series of
 * scenes in the world, lets each play for a moment, freezes the game ({@code /tick freeze}) and
 * moves the (spectator) camera into place. The client side (GiantClientHooks) waits for the
 * chunks to render and takes the screenshot. Server-side code; never active in normal play.
 */
public final class GiantShowcase {
    public static final boolean ENABLED = Boolean.getBoolean("giantrefresh.showcase");
    /** Only scenes whose name contains this text (empty = all). */
    private static final String ONLY = System.getProperty("giantrefresh.showcase.only", "");

    public static final String[] SCENES = {"hero", "grab", "village", "trail", "camp", "spiders", "potions", "ruins"};

    /** Index of the scene that is frozen and framed, read by the client. */
    public static volatile int ready = -1;

    private static final List<Entity> spawned = new ArrayList<>();
    private static BlockPos base;
    private static BlockPos site;
    private static BooleanSupplier until;
    private static int untilTicks;
    private static double[] camera;
    /** Trail scenes share one place: the trail and the sleeping Giant at its end. */
    private static BlockPos trailSite;
    private static GiantEntity trailGiant;
    private static double trailDirX;
    private static double trailDirZ;

    private GiantShowcase() {}

    public static boolean wanted(int scene) {
        return ONLY.isEmpty() || ONLY.contains(SCENES[scene]);
    }

    // ------------------------------------------------------------------ server tick

    public static void tick(ServerLevel level) {
        if (until == null || level != level.getServer().overworld()) {
            return;
        }
        untilTicks++;
        boolean done;
        try {
            done = until.getAsBoolean();
        } catch (RuntimeException e) {
            GiantRefresh.LOGGER.error("[SHOWCASE] scene action failed", e);
            done = true;
        }
        if (done || untilTicks > 400) {
            until = null;
            command(level.getServer(), "tick freeze");
            placeCamera(level);
        }
    }

    // ------------------------------------------------------------------ steps (server thread)

    /** Step 1: unfreeze, clean up, choose the place and fly the camera there so the chunks load. */
    public static void prepare(MinecraftServer server, int scene) {
        ServerLevel level = server.overworld();
        ServerPlayer player = player(level);
        if (player == null) {
            return;
        }
        ready = -1;
        command(server, "tick unfreeze");
        if (base == null) {
            base = player.blockPosition();
            GRConfig.get().screenShake = false;
            command(server, "gamemode spectator @a");
            command(server, "gamerule doMobSpawning false");
            command(server, "gamerule spawn_mobs false");
            command(server, "gamerule doDaylightCycle false");
            command(server, "gamerule advance_time false");
            command(server, "gamerule doWeatherCycle false");
            command(server, "gamerule advance_weather false");
            command(server, "weather clear");
        }
        for (Entity e : spawned) {
            if (e != trailGiant) {
                e.discard();
            }
        }
        spawned.removeIf(Entity::isRemoved);
        String name = SCENES[scene];
        if (name.equals("camp") && trailSite != null) {
            site = trailSite;
        } else {
            site = findSite(level, scene, name.equals("village"));
        }
        player.teleportTo(level, site.getX() + 0.5, 140.0, site.getZ() + 0.5, java.util.Set.of(), 0.0F, 30.0F, false);
        GiantRefresh.LOGGER.info("[SHOWCASE] scene {} '{}' at {} {}", scene, name, site.getX(), site.getZ());
    }

    /** Step 2 (a few seconds later, chunks are there): build the scene and start its action. */
    public static void stage(MinecraftServer server, int scene) {
        ServerLevel level = server.overworld();
        int x = site.getX();
        int z = site.getZ();
        int y = ground(level, x, z);
        if (!SCENES[scene].equals("camp")) {
            command(server, "execute positioned " + x + " " + y + " " + z + " run kill @e[type=!player,distance=..90]");
        }
        untilTicks = 0;
        switch (SCENES[scene]) {
            case "hero" -> {
                // golden hour, low camera looking up at a Giant with a crewed harness hat
                command(server, "time set 12300");
                GiantEntity g = giant(level, x, z, 200.0F, 14, true);
                g.setNoAi(true);
                g.setCustomName(net.minecraft.network.chat.Component.literal("pose:roar:12"));
                zombie(level, x - 6, z - 5, 20.0F);
                zombie(level, x + 5, z - 7, -15.0F);
                camera = lookAt(level, x - 5.5, y + 1.3, z - 15.5, x, y + 8.5, z);
                until = () -> untilTicks > 20;
            }
            case "grab" -> {
                // a Giant snatches a villager and lifts it over its head
                command(server, "time set 4500");
                GiantEntity g = giant(level, x, z, 0.0F, 4, false);
                LivingEntity victim = spawn(EntityTypes.VILLAGER, level, x, z + 3, 180.0F);
                if (victim instanceof Mob m) {
                    m.setNoAi(true);
                }
                g.setTarget(victim);
                untilTicks = 0;
                until = () -> {
                    if (untilTicks == 10) {
                        g.startAttack(GiantEntity.ATTACK_GRAB);
                    }
                    return g.getHeld() != null && g.getAttackTicks() >= dev.star1mp.giantrefresh.entity.anim.GiantPose.GRAB_THROW - 4;
                };
                camera = lookAt(level, x + 13.0, y + 7.0, z + 6.0, x, y + 11.0, z + 1.0);
            }
            case "village" -> {
                // in a village: the Giant raises both fists over an iron golem, villagers run
                command(server, "time set 6000");
                GiantEntity g = giant(level, x, z, 180.0F, 1, true);
                g.setNoAi(true);
                g.setCustomName(net.minecraft.network.chat.Component.literal("pose:punch:17"));
                LivingEntity golem = spawn(EntityTypes.IRON_GOLEM, level, x + 0.5, z - 5.0, 0.0F);
                if (golem instanceof Mob m) {
                    m.setNoAi(true);
                }
                for (int i = 0; i < 4; i++) {
                    LivingEntity v = spawn(EntityTypes.VILLAGER, level, x - 7 + i * 4.5, z - 12 - (i % 2) * 3, 180.0F + (i - 2) * 25.0F);
                    if (v instanceof Mob m) {
                        m.setNoAi(true);
                    }
                }
                camera = lookAt(level, x - 9.0, y + 3.0, z - 19.0, x, y + 6.5, z);
                until = () -> untilTicks > 20;
            }
            case "trail" -> {
                // a footprint trail from above, a trampled hut at its start, the sleeping Giant at its end
                command(server, "time set 5000");
                double ruin = GRConfig.get().trailRuinChance;
                GRConfig.get().trailRuinChance = 1.0;
                long seed = 7L;
                RandomSource dirRandom = RandomSource.create(seed);
                double angle = dirRandom.nextDouble() * Math.PI * 2.0;
                trailDirX = Math.cos(angle);
                trailDirZ = Math.sin(angle);
                GiantTrailFeature trail = new GiantTrailFeature(9, 9, 1.0F, 1.0F);
                trail.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed), new BlockPos(x, y, z));
                GRConfig.get().trailRuinChance = ruin;
                trailSite = site;
                int cx = (x & ~15) + 8;
                int cz = (z & ~15) + 8;
                trailGiant = level.getEntitiesOfClass(GiantEntity.class, new net.minecraft.world.phys.AABB(cx - 40, y - 20, cz - 40, cx + 40, y + 30, cz + 40))
                        .stream().findFirst().orElse(null);
                if (trailGiant != null) {
                    spawned.add(trailGiant);
                }
                // above and behind the old end of the trail, looking along it
                double bx = cx - trailDirX * 30.0;
                double bz = cz - trailDirZ * 30.0;
                camera = lookAt(level, bx, ground(level, (int) bx, (int) bz) + 22.0, bz, cx + trailDirX * 6.0, y, cz + trailDirZ * 6.0);
                until = () -> untilTicks > 20;
            }
            case "camp" -> {
                // dusk at the end of the trail: the Giant sleeps, a zombie keeps watch
                command(server, "time set 12800");
                if (trailGiant != null) {
                    double gx = trailGiant.getX();
                    double gz = trailGiant.getZ();
                    double gy = trailGiant.getY();
                    double side = -trailDirZ;
                    double sideZ = trailDirX;
                    zombie(level, gx + side * 5.0 - trailDirX * 3.0, gz + sideZ * 5.0 - trailDirZ * 3.0, 0.0F);
                    camera = lookAt(level, gx - trailDirX * 11.0 + side * 7.0, gy + 2.5, gz - trailDirZ * 11.0 + sideZ * 7.0, gx, gy + 3.5, gz);
                } else {
                    camera = lookAt(level, x - 10, y + 6, z - 10, x, y, z);
                }
                until = () -> untilTicks > 20;
            }
            case "spiders" -> {
                // moonlit night: spiders crawl up a Giant, one already sits on its head
                command(server, "time set 18000");
                GiantEntity g = giant(level, x, z, 160.0F, -1, false);
                g.setNoAi(true);
                g.setCustomName(net.minecraft.network.chat.Component.literal("pose:walk:2"));
                List<dev.star1mp.giantrefresh.entity.ai.ClimbGiantGoal> goals = new ArrayList<>();
                double[][] at = {{-6, 3}, {5, 6}, {-2, 8}, {7, -2}};
                for (double[] p : at) {
                    LivingEntity s = spawn(EntityTypes.SPIDER, level, x + p[0], z + p[1], 0.0F);
                    if (s instanceof net.minecraft.world.entity.monster.spider.Spider spider) {
                        var goal = new dev.star1mp.giantrefresh.entity.ai.ClimbGiantGoal(spider);
                        ((dev.star1mp.giantrefresh.mixin.MobAccessor) spider).giantrefresh$goalSelector().addGoal(0, goal);
                        goals.add(goal);
                    }
                }
                LivingEntity top = spawn(EntityTypes.SPIDER, level, x, z, 0.0F);
                if (top != null) {
                    top.startRiding(g);
                }
                until = () -> {
                    if (untilTicks == 5) {
                        goals.forEach(goal -> goal.forceClimb(g));
                    }
                    return untilTicks > 70;
                };
                camera = lookAt(level, x - 4.0, y + 4.0, z + 17.0, x, y + 7.5, z);
            }
            case "potions" -> {
                // Growth and Shrinking: a brewing stand in front, a giant-sized and a tiny zombie behind
                command(server, "time set 2500");
                BlockPos stand = new BlockPos(x, ground(level, x, z), z);
                level.setBlock(stand.below(), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
                BlockState bs = Blocks.BREWING_STAND.defaultBlockState();
                for (int i = 0; i < 3; i++) {
                    bs = bs.setValue(BrewingStandBlock.HAS_BOTTLE[i], true);
                }
                level.setBlock(stand, bs, 3);
                Holder<net.minecraft.world.item.alchemy.Potion> growth = BuiltInRegistries.POTION.wrapAsHolder(GRContent.GROWTH_POTION.get());
                Holder<net.minecraft.world.item.alchemy.Potion> shrinking = BuiltInRegistries.POTION.wrapAsHolder(GRContent.SHRINKING_POTION.get());
                if (level.getBlockEntity(stand) instanceof BrewingStandBlockEntity be) {
                    be.setItem(0, PotionContents.createItemStack(Items.POTION, growth));
                    be.setItem(1, PotionContents.createItemStack(Items.SPLASH_POTION, growth));
                    be.setItem(2, PotionContents.createItemStack(Items.POTION, shrinking));
                    be.setItem(3, new ItemStack(GRContent.GIANT_TOOTH.get()));
                }
                item(level, x + 1.3, z - 0.6, new ItemStack(GRContent.GIANT_TOOTH.get()));
                item(level, x - 1.2, z - 0.9, PotionContents.createItemStack(Items.POTION, shrinking));
                LivingEntity big = zombie(level, x + 4.0, z + 7.0, 200.0F);
                LivingEntity normal = zombie(level, x - 1.0, z + 6.0, 170.0F);
                LivingEntity small = zombie(level, x - 3.5, z + 4.0, 150.0F);
                if (big != null) {
                    big.addEffect(new MobEffectInstance(GRContent.growthHolder(), -1, 0, false, false));
                }
                if (small != null) {
                    small.addEffect(new MobEffectInstance(GRContent.shrinkingHolder(), -1, 0, false, false));
                }
                camera = lookAt(level, x - 1.6, stand.getY() + 1.1, z - 3.8, x + 0.6, stand.getY() + 1.8, z + 4.0);
                until = () -> untilTicks > 90;
            }
            case "ruins" -> {
                // a trampled settlement: the Giant stomps through what is left of the huts
                command(server, "time set 9500");
                GiantTrailFeature.placeTestRuins(level, level.getRandom(), x, z);
                GiantEntity g = giant(level, x + 1, z + 15, 200.0F, 9, true);
                g.setNoAi(true);
                g.setCustomName(net.minecraft.network.chat.Component.literal("pose:stomp:20"));
                camera = lookAt(level, x - 12.0, y + 6.0, z - 9.0, x + 0.5, y + 3.0, z + 7.0);
                until = () -> untilTicks > 20;
            }
            default -> until = () -> true;
        }
    }

    private static void placeCamera(ServerLevel level) {
        ServerPlayer player = player(level);
        if (player != null && camera != null) {
            player.teleportTo(level, camera[0], camera[1], camera[2], java.util.Set.of(), (float) camera[3], (float) camera[4], false);
        }
        GiantRefresh.LOGGER.info("[SHOWCASE] frozen, camera at {}", camera == null ? "-" : String.format("%.1f %.1f %.1f yaw %.1f pitch %.1f",
                camera[0], camera[1], camera[2], camera[3], camera[4]));
        ready = currentScene;
    }

    private static int currentScene = -1;

    public static void setCurrent(int scene) {
        currentScene = scene;
    }

    // ------------------------------------------------------------------ helpers

    private static BlockPos findSite(ServerLevel level, int scene, boolean village) {
        BlockPos from = base.offset(scene * 320, 0, (scene % 2) * 320);
        if (village) {
            BlockPos v = level.findNearestMapStructure(net.minecraft.tags.StructureTags.VILLAGE, from, 60, false);
            if (v != null) {
                return v;
            }
        }
        var found = level.findClosestBiome3d(GiantShowcase::nice, from, 2400, 32, 64);
        return found != null ? found.getFirst() : from;
    }

    private static boolean nice(Holder<Biome> biome) {
        return biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS) || biome.is(Biomes.MEADOW) || biome.is(Biomes.FLOWER_FOREST);
    }

    private static int ground(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static double[] lookAt(ServerLevel level, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x;
        double dy = ty - y;
        double dz = tz - z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        // eye height of the camera is added by the teleport
        return new double[] {x, y - 1.62, z, yaw, pitch};
    }

    private static GiantEntity giant(ServerLevel level, double x, double z, float yaw, int hat, boolean riders) {
        GiantEntity g = GRContent.GIANT.get().create(level, EntitySpawnReason.COMMAND);
        g.snapTo(x + 0.5, ground(level, (int) Math.floor(x), (int) Math.floor(z)), z + 0.5, yaw, 0.0F);
        g.yHeadRot = yaw;
        g.yBodyRot = yaw;
        g.setHatColor(hat);
        if (riders && hat >= 0) {
            GiantRiders.spawnRiders(g, level, level.getCurrentDifficultyAt(g.blockPosition()));
            for (Entity p : g.getPassengers()) {
                if (p instanceof Mob mob) {
                    mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    mob.setPersistenceRequired();
                    spawned.add(mob);
                }
            }
        }
        g.setPersistenceRequired();
        level.addFreshEntityWithPassengers(g);
        spawned.add(g);
        return g;
    }

    private static LivingEntity zombie(ServerLevel level, double x, double z, float yaw) {
        LivingEntity zombie = spawn(EntityTypes.ZOMBIE, level, x, z, yaw);
        if (zombie instanceof Mob m) {
            m.setNoAi(true);
        }
        return zombie;
    }

    private static <T extends Entity> LivingEntity spawn(EntityType<T> type, ServerLevel level, double x, double z, float yaw) {
        T e = type.create(level, EntitySpawnReason.COMMAND);
        if (!(e instanceof LivingEntity living)) {
            return null;
        }
        e.snapTo(x, ground(level, (int) Math.floor(x), (int) Math.floor(z)), z, yaw, 0.0F);
        living.yHeadRot = yaw;
        living.yBodyRot = yaw;
        if (e instanceof Mob m) {
            m.setPersistenceRequired();
        }
        level.addFreshEntity(e);
        spawned.add(e);
        return living;
    }

    private static void item(ServerLevel level, double x, double z, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, x, ground(level, (int) Math.floor(x), (int) Math.floor(z)) + 0.05, z, stack, 0, 0, 0);
        item.setNeverPickUp();
        item.setUnlimitedLifetime();
        level.addFreshEntity(item);
        spawned.add(item);
    }

    private static ServerPlayer player(ServerLevel level) {
        return level.players().isEmpty() ? null : level.players().get(0);
    }

    private static void command(MinecraftServer server, String cmd) {
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
        } catch (RuntimeException e) {
            GiantRefresh.LOGGER.warn("[SHOWCASE] command failed: {}", cmd);
        }
    }
}
