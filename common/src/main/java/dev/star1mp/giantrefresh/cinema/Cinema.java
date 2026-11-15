package dev.star1mp.giantrefresh.cinema;

import dev.star1mp.giantrefresh.GiantRefresh;
import dev.star1mp.giantrefresh.config.GRConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Trailer recording ({@code -Dgiantrefresh.cinema=<shot>}, dev client only). The server stages the
 * shot and plays its script; the game runs slowed down ({@code /tick rate}) so the software renderer
 * can catch every frame, and the client grabs a picture whenever the game clock passes the next
 * 1/24 s (see CinemaClient). Never active in normal play.
 */
public final class Cinema {
    public static final String SHOT = System.getProperty("giantrefresh.cinema", "");
    public static final boolean ENABLED = !SHOT.isEmpty();
    public static final float TICK_RATE = Float.parseFloat(System.getProperty("giantrefresh.cinema.rate", "1"));
    public static final int FPS = Integer.getInteger("giantrefresh.cinema.fps", 24);

    public static volatile Shot shot;
    /** Game time of t = 0; -1 until the recording starts. */
    public static volatile long startTick = -1;
    public static volatile boolean clientReady;
    public static volatile boolean done;
    /** 0 waiting for the player, 1 travelling, 2 built, waiting for the client, 3 recording */
    public static volatile int phase;
    private static int wait;

    private Cinema() {}

    public static void serverTick(ServerLevel level) {
        if (!ENABLED || level != level.getServer().overworld()) {
            return;
        }
        MinecraftServer server = level.getServer();
        ServerPlayer player = level.players().isEmpty() ? null : level.players().get(0);
        if (player == null) {
            return;
        }
        switch (phase) {
            case 0 -> {
                if (++wait < 40) {
                    return;
                }
                shot = Shots.create(SHOT);
                GRConfig.get().screenShake = false;
                for (String c : new String[] {"gamemode spectator @a", "gamerule doMobSpawning false", "gamerule spawn_mobs false",
                        "gamerule doDaylightCycle false", "gamerule advance_time false", "gamerule doWeatherCycle false",
                        "gamerule advance_weather false", "weather clear", "gamerule doFireTick false", "gamerule mobGriefing true"}) {
                    command(server, c);
                }
                BlockPos site = shot.village ? village(level) : new BlockPos(shot.siteX, 0, shot.siteZ);
                shot.siteX = site.getX();
                shot.siteZ = site.getZ();
                player.teleportTo(level, site.getX() + 0.5, 150, site.getZ() + 0.5, java.util.Set.of(), 0, 40, false);
                phase = 1;
                wait = 0;
                GiantRefresh.LOGGER.info("[CINEMA] shot '{}' at {} {}", SHOT, site.getX(), site.getZ());
            }
            case 1 -> {
                if (++wait < 200) {
                    return;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, shot.siteX, shot.siteZ);
                shot.origin = new BlockPos(shot.siteX, y, shot.siteZ);
                shot.level = level;
                command(server, "execute positioned " + shot.siteX + " " + y + " " + shot.siteZ + " run kill @e[type=!player,distance=..120]");
                command(server, "time set " + shot.dayTime);
                shot.build();
                moveCamera(level, player, 0);
                phase = 2;
                wait = 0;
                GiantRefresh.LOGGER.info("[CINEMA] built, origin {}", shot.origin);
            }
            case 2 -> {
                moveCamera(level, player, 0);
                if (++wait > 60 && clientReady) {
                    command(server, "tick rate " + TICK_RATE);
                    startTick = level.getGameTime() + 1;
                    phase = 3;
                    GiantRefresh.LOGGER.info("[CINEMA] recording from game time {}", startTick);
                }
            }
            default -> {
                int t = (int) (level.getGameTime() - startTick);
                if (t < 0) {
                    return;
                }
                shot.tick(t);
                moveCamera(level, player, t / 20.0);
            }
        }
    }

    private static void moveCamera(ServerLevel level, ServerPlayer player, double sec) {
        Vec3 p = shot.cam.pose(sec).pos();
        clearPlants(level, p, 1.8);
        if (player.position().distanceToSqr(p) > 4) {
            player.teleportTo(level, p.x, p.y - 1.62, p.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        }
    }

    /** Grass and flowers right in front of the lens would fill the frame: remove them. */
    private static void clearPlants(ServerLevel level, Vec3 p, double r) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -2; dy <= 1; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    m.set(Math.floor(p.x) + dx, Math.floor(p.y) + dy, Math.floor(p.z) + dz);
                    var st = level.getBlockState(m);
                    if (!st.isAir() && st.getFluidState().isEmpty() && st.getCollisionShape(level, m).isEmpty()
                            && (st.is(net.minecraft.tags.BlockTags.FLOWERS) || st.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS)
                            || st.is(net.minecraft.world.level.block.Blocks.TALL_GRASS) || st.is(net.minecraft.world.level.block.Blocks.FERN)
                            || st.is(net.minecraft.world.level.block.Blocks.LARGE_FERN))) {
                        level.setBlock(m, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static BlockPos village(ServerLevel level) {
        var plains = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .get(net.minecraft.world.level.levelgen.structure.BuiltinStructures.VILLAGE_PLAINS);
        if (plains.isPresent()) {
            var v = level.getChunkSource().getGenerator().findNearestMapStructure(level,
                    net.minecraft.core.HolderSet.direct(plains.get()), new BlockPos(0, 64, 0), 80, false);
            if (v != null) {
                return v.getFirst();
            }
        }
        return new BlockPos(64, 0, -400);
    }

    public static void command(MinecraftServer server, String cmd) {
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
        } catch (RuntimeException e) {
            GiantRefresh.LOGGER.warn("[CINEMA] command failed: {}", cmd);
        }
    }
}
