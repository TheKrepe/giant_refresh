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
    private static net.minecraft.world.entity.LivingEntity grower;
    private static net.minecraft.world.entity.LivingEntity shrinker;
    private static net.minecraft.world.entity.LivingEntity mixer;
    private static double mixedBoth = -1;
    private static double mixedAfter = -1;
    private static boolean giantImmune;
    private static GiantEntity climbTarget;
    private static net.minecraft.world.entity.monster.spider.Spider climber;
    private static net.minecraft.world.entity.animal.golem.IronGolem golem;
    private static boolean golemSawGiant;
    private static GiantEntity grabber;
    private static net.minecraft.world.entity.animal.pig.Pig perched;
    private static boolean pigGrabbed;
    private static boolean pigThrown;
    private static double pigMaxY = -999;

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
            grower = EntityTypes.COW.spawn(level, pos.offset(0, 0, -10), EntitySpawnReason.COMMAND);

            shrinker = EntityTypes.COW.spawn(level, pos.offset(4, 0, -10), EntitySpawnReason.COMMAND);
            // far from the Giants, so nobody grabs or kills it before it is measured
            mixer = EntityTypes.COW.spawn(level, pos.offset(-40, 0, -40), EntitySpawnReason.COMMAND);
            if (mixer != null) {
                if (mixer instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setNoAi(true);
                }
            }

            // a hatless, frozen Giant for a spider to climb, and an iron golem that must pick a Giant as its target
            BlockPos cpos = pos.offset(0, 0, 34);
            climbTarget = buildGiant(level, cpos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cpos.getX(), cpos.getZ()), cpos.getZ(), -1, false);
            if (climbTarget != null) {
                climbTarget.setNoAi(true);
                climber = EntityTypes.SPIDER.spawn(level, cpos.offset(6, 0, 0), EntitySpawnReason.COMMAND);
                if (climber != null) {
                    climber.setPersistenceRequired();
                    dev.star1mp.giantrefresh.entity.ai.ClimbGiantGoal goal = new dev.star1mp.giantrefresh.entity.ai.ClimbGiantGoal(climber);
                    ((dev.star1mp.giantrefresh.mixin.MobAccessor) climber).giantrefresh$goalSelector().addGoal(1, goal);
                    goal.forceClimb(climbTarget);
                }
            }
            // a pig on a 10-block pillar: the Giant has to pluck it off and throw it down
            BlockPos gpos = pos.offset(32, 0, -18);
            int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gpos.getX(), gpos.getZ());
            for (int i = 0; i < 10; i++) {
                level.setBlock(new BlockPos(gpos.getX() + 3, gy + i, gpos.getZ()), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
            }
            perched = EntityTypes.PIG.spawn(level, new BlockPos(gpos.getX() + 3, gy + 10, gpos.getZ()), EntitySpawnReason.COMMAND);
            grabber = buildGiant(level, gpos.getX() - 3, gy, gpos.getZ(), -1, false);
            if (perched != null && grabber != null) {
                perched.setNoAi(true);
                perched.setPersistenceRequired();
                grabber.setTarget(perched);
            }
            // a villager next to awake Giants: loads (and so verifies) the villager-sensor mixin
            EntityTypes.VILLAGER.spawn(level, pos.offset(-5, 0, 26), EntitySpawnReason.COMMAND);
            golem = EntityTypes.IRON_GOLEM.spawn(level, pos.offset(-20, 0, -10), EntitySpawnReason.COMMAND);
            GiantEntity sleeper = GRContent.GIANT.get().spawn(level, pos.offset(-12, 0, 0), EntitySpawnReason.SPAWN_ITEM_USE);
            if (sleeper != null) {
                sleeper.setGiantSleeping(true);
            }
        }
        if (perched != null && grabber != null) {
            if (grabber.getTarget() != perched && !pigGrabbed && perched.isAlive()) {
                grabber.setTarget(perched);
            }
            if (perched.getVehicle() == grabber) {
                pigGrabbed = true;
                pigMaxY = Math.max(pigMaxY, perched.getY() - grabber.getY());
            } else if (pigGrabbed && !pigThrown) {
                pigThrown = true;
                GiantRefresh.LOGGER.info("[SMOKETEST] pig thrown from dy={} with motion {}", String.format("%.2f", perched.getY() - grabber.getY()), perched.getDeltaMovement());
                grabber.setTarget(null);
            }
        }
        if (golem != null && golem.getTarget() instanceof GiantEntity) {
            golemSawGiant = true;
        }
        if (ticks == 110 && grower != null && shrinker != null) {
            // drunk a little after spawning (a just-loaded entity gets its size back at once instead)
            grower.addEffect(new MobEffectInstance(GRContent.growthHolder(), 200));
            shrinker.addEffect(new MobEffectInstance(GRContent.shrinkingHolder(), 200));
            if (mixer != null) {
                // both at once: normal size; Growth lasts longer, so it grows once Shrinking runs out
                mixer.addEffect(new MobEffectInstance(GRContent.growthHolder(), 400));
                mixer.addEffect(new MobEffectInstance(GRContent.shrinkingHolder(), 200));
            }
        }
        if (mixer != null && mixer.isAlive() && ticks == 200) {
            mixedBoth = mixer.getAttributeValue(Attributes.SCALE);
        }
        if (mixer != null && mixer.isAlive() && ticks == 420) {
            mixedAfter = mixer.getAttributeValue(Attributes.SCALE);
        }
        if (grower != null && shrinker != null && ticks > 100 && ticks <= 400 && ticks % 5 == 0) {
            GiantRefresh.LOGGER.info("[SMOKETEST] size t={} growth={} shrinking={}", ticks - 110,
                    String.format("%.3f", grower.getAttributeValue(Attributes.SCALE)), String.format("%.3f", shrinker.getAttributeValue(Attributes.SCALE)));
        }
        if (ticks == 120 && climbTarget != null) {
            climbTarget.addEffect(new MobEffectInstance(GRContent.growthHolder(), 200));
            climbTarget.addEffect(new MobEffectInstance(GRContent.shrinkingHolder(), 200));
            giantImmune = !climbTarget.hasEffect(GRContent.growthHolder()) && !climbTarget.hasEffect(GRContent.shrinkingHolder());
            GiantRefresh.LOGGER.info("[SMOKETEST] Giant ignores Growth/Shrinking: {}", giantImmune);
        }
        if (ticks == 500) {
            AABB area = new AABB(pos).inflate(96);
            int giants = level.getEntitiesOfClass(GiantEntity.class, area).size();
            int vanilla = level.getEntitiesOfClass(net.minecraft.world.entity.monster.Giant.class, area).size();
            int riders = 0;
            for (GiantEntity g : level.getEntitiesOfClass(GiantEntity.class, area)) {
                riders += g.getPassengers().size();
                for (net.minecraft.world.entity.Entity p : g.getPassengers()) {
                    GiantRefresh.LOGGER.info("[SMOKETEST] rider {} burning={} cap={}", p.getType().getDescriptionId(), p.isOnFire(),
                            p instanceof net.minecraft.world.entity.LivingEntity le ? le.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getHoverName().getString() : "-");
                    GiantRefresh.LOGGER.info("[SMOKETEST] rider {} offset dy={} dx={} dz={}", p.getType().getDescriptionId(),
                            String.format("%.2f", p.getY() - g.getY()), String.format("%.2f", p.getX() - g.getX()), String.format("%.2f", p.getZ() - g.getZ()));
                }
            }
            boolean spiderOnHead = climber != null && climber.getVehicle() == climbTarget;
            GiantRefresh.LOGGER.info("[SMOKETEST] spider climbed onto the Giant: {} (spider at dy={})", spiderOnHead,
                    climber != null && climbTarget != null ? String.format("%.2f", climber.getY() - climbTarget.getY()) : "-");
            GiantRefresh.LOGGER.info("[SMOKETEST] iron golem targeted a Giant: {}", golemSawGiant);
            GiantRefresh.LOGGER.info("[SMOKETEST] pillar pig grabbed={} thrown={} highestHold={} pigAlive={} pigY={}", pigGrabbed, pigThrown,
                    String.format("%.2f", pigMaxY), perched != null && perched.isAlive(),
                    perched != null && grabber != null ? String.format("%.2f", perched.getY() - grabber.getY()) : "-");
            double scale = grower != null && grower.isAlive() ? grower.getAttributeValue(Attributes.SCALE) : -1;
            boolean content = contentCheck(level);
            boolean bothCancel = Math.abs(mixedBoth - 1.0) < 0.05 && mixedAfter > 2.0;
            GiantRefresh.LOGGER.info("[SMOKETEST] Growth+Shrinking together: scale={} (normal 1.0), after Shrinking ended: {} -> {}",
                    String.format("%.3f", mixedBoth), String.format("%.3f", mixedAfter), bothCancel ? "OK" : "WRONG");
            boolean pass = giants >= 4 && vanilla == 0 && giantImmune && content && bothCancel;
            GiantRefresh.LOGGER.info("[SMOKETEST] giants={} vanillaGiants={} riders={} zombieScaleAfterEffect={} -> {}",
                    giants, vanilla, riders, scale, pass ? "PASS" : "FAIL");
            level.getServer().halt(false);
        }
    }

    /** Data-pack content that must be identical on every supported version: worldgen, loot, brewing, tags. */
    private static boolean contentCheck(ServerLevel level) {
        var server = level.getServer();
        var access = level.registryAccess();
        var placed = access.lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);
        boolean footprints = placed.get(GRContent.key(net.minecraft.core.registries.Registries.PLACED_FEATURE, "giant_footprints")).isPresent();
        boolean camp = placed.get(GRContent.key(net.minecraft.core.registries.Registries.PLACED_FEATURE, "giant_camp")).isPresent();
        int habitat = access.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).get(GRContent.GIANT_BIOMES)
                .map(set -> set.size()).orElse(0);
        var loot = server.reloadableRegistries();
        boolean giantLoot = loot.getLootTable(GRContent.key(net.minecraft.core.registries.Registries.LOOT_TABLE, "entities/giant"))
                != net.minecraft.world.level.storage.loot.LootTable.EMPTY;
        boolean headLoot = loot.getLootTable(GRContent.key(net.minecraft.core.registries.Registries.LOOT_TABLE, "blocks/giant_head"))
                != net.minecraft.world.level.storage.loot.LootTable.EMPTY;
        int modRecipes = 0;
        int brewingRecipes = 0;
        for (var holder : server.getRecipeManager().getRecipes()) {
            var id = holder.id().identifier();
            if (id.getNamespace().equals(GiantRefresh.MOD_ID)) {
                if (id.getPath().startsWith("brewing/")) {
                    brewingRecipes++;
                } else {
                    modRecipes++;
                }
            }
        }
        var awkward = net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,
                net.minecraft.world.item.alchemy.Potions.AWKWARD);
        var growthPotion = net.minecraft.core.registries.BuiltInRegistries.POTION.wrapAsHolder(GRContent.GROWTH_POTION.get());
        var growthStack = net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION, growthPotion);
        boolean brewGrowth;
        boolean brewShrinking;
        //#if MC >= 26.3
        brewGrowth = brewingRecipes >= 23
                && server.getRecipeManager().byKey(GRContent.key(net.minecraft.core.registries.Registries.RECIPE, "brewing/potion_awkward_giant_tooth")).isPresent();
        brewShrinking = server.getRecipeManager().byKey(GRContent.key(net.minecraft.core.registries.Registries.RECIPE, "brewing/potion_growth_fermented_spider_eye")).isPresent();
        //#else
        //$$ var brewing = level.potionBrewing();
        //$$ var shrinkingPotion = net.minecraft.core.registries.BuiltInRegistries.POTION.wrapAsHolder(GRContent.SHRINKING_POTION.get());
        //$$ var out1 = brewing.mix(new net.minecraft.world.item.ItemStack(GRContent.GIANT_TOOTH.get()), awkward);
        //$$ var out2 = brewing.mix(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FERMENTED_SPIDER_EYE), growthStack);
        //$$ brewGrowth = out1.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
        //$$         net.minecraft.world.item.alchemy.PotionContents.EMPTY).is(growthPotion);
        //$$ brewShrinking = out2.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
        //$$         net.minecraft.world.item.alchemy.PotionContents.EMPTY).is(shrinkingPotion);
        //#endif
        boolean ok = footprints && camp && habitat > 0 && giantLoot && headLoot && brewGrowth && brewShrinking;
        GiantRefresh.LOGGER.info("[SMOKETEST] content placedFeatures={}/{} habitatBiomes={} loot={}/{} recipes={} brewingRecipes={} brewGrowth={} brewShrinking={} -> {}",
                footprints, camp, habitat, giantLoot, headLoot, modRecipes, brewingRecipes, brewGrowth, brewShrinking, ok ? "OK" : "MISSING");
        return ok;
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
                for (int cz = (pz >> 4) - 1; cz <= (pz >> 4) + 10; cz++) {
                    level.setChunkForced(cx, cz, true);
                }
            }
            int py = 160;
            stageX = px;
            stageZ = pz;
            for (int x = px - 26; x <= px + 26; x++) {
                for (int z = pz - 6; z <= pz + 140; z++) {
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
            if (puncher != null) {
                puncher.setNoAi(true);
            }
            // showcase rows with frozen animation poses (see GiantRenderer, test mode only)
            String[][] rows = {
                    {"pose:walk:3", "pose:punch:18", "pose:punch:23"},
                    {"pose:stomp:21", "pose:scoop:15", "pose:roar:14"},
                    {"pose:grab:16", "pose:grab:31", "pose:death:14"},
            };
            for (int r = 0; r < rows.length; r++) {
                for (int i = 0; i < 3; i++) {
                    GiantEntity g = buildGiant(level, px - 10 + i * 10, py, pz + 60 + r * 30, (r * 3 + i) % 16, false);
                    if (g != null) {
                        g.setNoAi(true);
                        g.setYRot(125.0F);
                        g.yBodyRot = 125.0F;
                        g.yHeadRot = 125.0F;
                        g.setCustomName(net.minecraft.network.chat.Component.literal(rows[r][i]));
                    }
                }
            }
            // a trampled settlement at the side of the stage (shot 8)
            dev.star1mp.giantrefresh.worldgen.GiantTrailFeature.placeTestRuins(level, level.getRandom(), px + 19, pz + 44);
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
            case 4, 5, 6 -> { y = 166; z += 60 + (shot - 4) * 30 - 17; yaw = 0; pitch = 10; }
            case 7 -> { y = 166; z += 17 + 18; yaw = 180; pitch = 6; }
            case 8 -> { x += 19 - 10; y = 167; z += 47; yaw = -90; pitch = 36; }
            default -> {}
        }
        player.teleportTo(level, x, y, z, java.util.Set.of(), yaw, pitch, false);
        GiantRefresh.LOGGER.info("[CLIENTTEST] camera {} at {} {} {}", shot, x, y, z);
    }
}
