package dev.star1mp.giantrefresh.entity;

import dev.star1mp.giantrefresh.GiantRefresh;
import dev.star1mp.giantrefresh.registry.GRContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * CI-only self test, enabled with {@code -Dgiantrefresh.smoketest=true} on a dedicated server:
 * spawns Giants (with hat/riders), a vanilla Giant (must be replaced) and a zombie under the
 * Growth effect, lets them tick for a while, logs the result and stops the server.
 */
public final class GiantSmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("giantrefresh.smoketest");
    private static final boolean CLIENT_MODE = Boolean.getBoolean("giantrefresh.clientsmoketest");
    private static int ticks;
    private static int clientTicks;
    private static GiantEntity puncher;
    private static int stageX;
    private static int stageZ;
    private static Zombie grower;

    private GiantSmokeTest() {}

    public static void tick(ServerLevel level) {
        if (CLIENT_MODE && level.dimension() == Level.OVERWORLD) {
            clientTick(level);
            return;
        }
        if (!ENABLED || level.dimension() != Level.OVERWORLD) {
            return;
        }
        ticks++;
        if (ticks == 1) {
            for (int cx = -2; cx <= 3; cx++) {
                for (int cz = -2; cz <= 3; cz++) {
                    level.setChunkForced(cx, cz, true);
                }
            }
        }
        BlockPos base = new BlockPos(8, 0, 8);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ());
        BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
        if (ticks == 100) {
            GiantRefresh.LOGGER.info("[SMOKETEST] spawning test entities at {}", pos);
            for (int i = 0; i < 3; i++) {
                BlockPos at = pos.offset(i * 8, 0, 0);
                GiantEntity g = buildGiant(level, at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ(), i * 5, true);
                if (g != null) {
                    GiantRefresh.LOGGER.info("[SMOKETEST] giant {} riders={}", i, g.getPassengers().size());
                }
            }
            EntityTypes.GIANT.spawn(level, pos.offset(0, 0, 12), EntitySpawnReason.COMMAND);
            grower = EntityTypes.ZOMBIE.spawn(level, pos.offset(0, 0, -10), EntitySpawnReason.COMMAND);
            if (grower != null) {
                grower.addEffect(new MobEffectInstance(GRContent.growthHolder(), 200));
            }
            GiantEntity sleeper = GRContent.GIANT.get().spawn(level, pos.offset(-12, 0, 0), EntitySpawnReason.SPAWN_ITEM_USE);
            if (sleeper != null) {
                sleeper.setGiantSleeping(true);
            }
        }
        if (ticks == 220 && grower != null) {
            GiantRefresh.LOGGER.info("[SMOKETEST] zombie scale under Growth = {}", grower.getAttributeValue(Attributes.SCALE));
        }
        if (ticks == 500) {
            AABB area = new AABB(pos).inflate(96);
            int giants = level.getEntitiesOfClass(GiantEntity.class, area).size();
            int vanilla = level.getEntitiesOfClass(net.minecraft.world.entity.monster.Giant.class, area).size();
            int riders = 0;
            for (GiantEntity g : level.getEntitiesOfClass(GiantEntity.class, area)) {
                riders += g.getPassengers().size();
                for (net.minecraft.world.entity.Entity p : g.getPassengers()) {
                    GiantRefresh.LOGGER.info("[SMOKETEST] rider {} offset dy={} dx={} dz={}", p.getType().getDescriptionId(),
                            String.format("%.2f", p.getY() - g.getY()), String.format("%.2f", p.getX() - g.getX()), String.format("%.2f", p.getZ() - g.getZ()));
                }
            }
            double scale = grower != null && grower.isAlive() ? grower.getAttributeValue(Attributes.SCALE) : -1;
            boolean pass = giants >= 4 && vanilla == 0;
            GiantRefresh.LOGGER.info("[SMOKETEST] giants={} vanillaGiants={} riders={} zombieScaleAfterEffect={} -> {}",
                    giants, vanilla, riders, scale, pass ? "PASS" : "FAIL");
            level.getServer().halt(false);
        }
    }

    /** Integrated-server half of the client render test: stage a scene in front of the player. */
    private static void clientTick(ServerLevel level) {
        if (level.players().isEmpty()) {
            return;
        }
        clientTicks++;
        net.minecraft.server.level.ServerPlayer player = level.players().get(0);
        if (clientTicks == 20) {
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            int px = net.minecraft.util.Mth.floor(player.getX());
            int pz = net.minecraft.util.Mth.floor(player.getZ());
            for (int cx = (px >> 4) - 2; cx <= (px >> 4) + 3; cx++) {
                for (int cz = (pz >> 4) - 1; cz <= (pz >> 4) + 8; cz++) {
                    level.setChunkForced(cx, cz, true);
                }
            }
            int py = 160;
            stageX = px;
            stageZ = pz;
            for (int x = px - 26; x <= px + 26; x++) {
                for (int z = pz - 6; z <= pz + 112; z++) {
                    level.setBlock(new BlockPos(x, py - 1, z), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                }
            }
            player.teleportTo(level, px + 0.5, py, pz + 0.5, java.util.Set.of(), 0.0F, -24.0F, false);
            GiantEntity a = buildGiant(level, px, py, pz + 17, 14, true);
            if (a != null) {
                a.setNoAi(true);
            }
            GiantEntity b = buildGiant(level, px + 13, py, pz + 22, 3, false);
            if (b != null) {
                b.setGiantSleeping(true);
            }
            puncher = buildGiant(level, px - 12, py, pz + 20, -1, false);
            // showcase rows with frozen animation poses (see GiantRenderer, test mode only)
            String[][] rows = {
                    {"pose:walk:3", "pose:punch:19", "pose:punch:23"},
                    {"pose:stomp:21", "pose:scoop:17", "pose:roar:14"},
                    {"pose:gesture1:30", "pose:gesture4:24", "pose:death:14"},
            };
            for (int r = 0; r < rows.length; r++) {
                for (int i = 0; i < 3; i++) {
                    GiantEntity g = buildGiant(level, px - 10 + i * 10, py, pz + 40 + r * 30, (r * 3 + i) % 16, false);
                    if (g != null) {
                        g.setNoAi(true);
                        g.setYRot(125.0F);
                        g.yBodyRot = 125.0F;
                        g.yHeadRot = 125.0F;
                        g.setCustomName(net.minecraft.network.chat.Component.literal(rows[r][i]));
                    }
                }
            }
            GiantRefresh.LOGGER.info("[CLIENTTEST] scene staged around {} {}", px, pz);
        }
        if (clientTicks >= 21 && !player.getAbilities().flying) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
        if (puncher != null && clientTicks > 40 && clientTicks % 50 == 0) {
            puncher.startAttack(clientTicks % 100 == 0 ? GiantEntity.ATTACK_PUNCH : GiantEntity.ATTACK_STOMP);
        }
    }

    /** Creates a Giant the same way natural spawning does, then forces hat colour / riders. */
    private static GiantEntity buildGiant(ServerLevel level, int x, int y, int z, int hat, boolean riders) {
        GiantEntity g = GRContent.GIANT.get().create(level, EntitySpawnReason.COMMAND);
        if (g == null) {
            return null;
        }
        g.snapTo(x + 0.5, y, z + 0.5, 180.0F, 0.0F);
        g.yHeadRot = 180.0F;
        g.yBodyRot = 180.0F;
        g.setHatColor(hat);
        if (riders && hat >= 0) {
            GiantRiders.spawnRiders(g, level, level.getCurrentDifficultyAt(g.blockPosition()));
            for (net.minecraft.world.entity.Entity p : g.getPassengers()) {
                if (p instanceof net.minecraft.world.entity.Mob mob) {
                    // keep the test crew alive in daylight
                    mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
                    mob.setPersistenceRequired();
                }
            }
        }
        g.setPersistenceRequired();
        level.addFreshEntityWithPassengers(g);
        return g;
    }

    /** Render test: camera position for screenshot {@code shot}; called on the server thread. */
    public static void showcaseCamera(ServerLevel level, int shot) {
        if (level.players().isEmpty()) {
            return;
        }
        net.minecraft.server.level.ServerPlayer player = level.players().get(0);
        double x = stageX + 0.5;
        double z = stageZ + 0.5;
        double y = 160.0;
        float yaw = 0.0F;
        float pitch = -24.0F;
        switch (shot) {
            case 1 -> { x += 5; y = 171; z += 9; yaw = 32; pitch = 4; }
            case 2 -> { x += 13 + 17; y = 164; z += 22; yaw = 90; pitch = 8; }
            case 3 -> { x += 13; y = 165; z += 12; yaw = 0; pitch = 14; }
            case 4, 5, 6 -> { y = 166; z += 40 + (shot - 4) * 30 - 24; yaw = 0; pitch = 6; }
            case 7 -> { y = 166; z += 17 + 18; yaw = 180; pitch = 6; }
            default -> {}
        }
        player.teleportTo(level, x, y, z, java.util.Set.of(), yaw, pitch, false);
        GiantRefresh.LOGGER.info("[CLIENTTEST] camera {} at {} {} {}", shot, x, y, z);
    }
}
