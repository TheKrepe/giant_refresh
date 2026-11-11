package dev.star1mp.giantrefresh.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.star1mp.giantrefresh.GiantRefresh;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple JSON config: config/giant_refresh.json.
 * Missing keys fall back to defaults and the file is rewritten with every key present.
 */
public final class GRConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static GRConfig instance = new GRConfig();

    private static final int CURRENT_VERSION = 2;
    /** Used to migrate old defaults; do not edit. */
    public int configVersion = CURRENT_VERSION;

    // --- Giant stats ---
    public double giantMaxHealth = 200.0;
    public double giantPunchDamage = 15.0;
    public double giantStompDamage = 9.0;
    /** Grab-and-throw: the Giant snatches targets it cannot hit otherwise (e.g. on a pillar) and hurls them down. */
    public boolean giantGrabEnabled = true;
    /** Damage when the fist closes; the fall after the throw does the rest. */
    public double giantGrabDamage = 6.0;
    /** Multiplier for how hard grabbed creatures are thrown down. */
    public double giantThrowStrength = 1.0;
    public double giantMovementSpeed = 0.2;
    public double giantFollowRange = 64.0;

    // --- World interaction ---
    /** Break leaves, flowers and light wooden structures that block the Giant (also needs gamerule mobGriefing). */
    public boolean breakBlocks = true;
    /** Crush flowers / tall grass / crops under the Giant's feet. */
    public boolean crushPlants = true;
    /** Walking Giants leave coarse-dirt footprints on grass. */
    public boolean leaveFootprints = true;
    /** Chance (0..1) that a single step leaves a footprint. */
    public double footprintChance = 0.35;

    // --- Spawning ---
    public boolean naturalSpawning = true;
    /** How often (in ticks) the spawner tries to place a Giant near each player. */
    public int spawnAttemptInterval = 1200;
    /** Chance that an attempt succeeds (before terrain checks). */
    public double spawnChance = 0.06;
    public int minSpawnDistance = 48;
    public int maxSpawnDistance = 96;
    /** No new Giant spawns if one already exists within this radius of the player. */
    public int giantExclusionRadius = 160;
    /** Generate footprint trails and sleeping-Giant camps in the world. */
    public boolean worldgenTrails = true;
    /** Chance (0..1) that a trail starts at a small settlement the Giant has trampled. */
    public double trailRuinChance = 0.3;

    // --- Hat & riders ---
    public double hatChance = 0.6;
    public double ridersChance = 0.85;
    public double hordeChance = 0.35;

    // --- Mob behaviour ---
    public boolean replaceVanillaGiant = true;
    public boolean zombiesFollowGiants = true;
    public boolean villagersFleeGiants = true;
    /** Villagers near an awake Giant hide in their houses, as if the bell had been rung. */
    public boolean villagersHideFromGiants = true;
    /** Spiders climb up the Giant's body and ride on its head, pouncing from above. */
    public boolean spidersClimbGiants = true;
    /** A Giant stepping right next to a creeper startles it into exploding. */
    public boolean creepersStartledByGiants = true;
    /** Iron golems treat Giants as their most important target and spot them from far away. */
    public boolean golemsHuntGiants = true;

    // --- Client ---
    public boolean screenShake = true;
    public double screenShakeStrength = 1.0;

    // --- Potion of Growth ---
    /** Extra scale added at full strength (1.75 => ~2.75x => ~5 blocks tall for a player). */
    public double growthScaleBonus = 1.75;
    public double growthSpeedPenalty = 0.15;
    public double growthDamageBonus = 0.30;
    // --- Potion of Shrinking ---
    /** Size under Shrinking, as a fraction of the normal size. */
    public double shrinkingScale = 0.5;
    /** Melee damage lost while fully shrunk. */
    public double shrinkingDamagePenalty = 0.30;

    public static GRConfig get() {
        return instance;
    }

    public static void load(Path configDir) {
        Path file = configDir.resolve(GiantRefresh.MOD_ID + ".json");
        GRConfig loaded = null;
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    loaded = GSON.fromJson(reader, GRConfig.class);
                }
            }
        } catch (Exception e) {
            GiantRefresh.LOGGER.warn("Could not read {}, using defaults: {}", file, e.toString());
        }
        instance = loaded != null ? loaded : new GRConfig();
        if (instance.configVersion < 2) {
            // 1.0.0 shipped with 400 HP; the Giant now has 200 HP by default.
            if (instance.giantMaxHealth == 400.0) {
                instance.giantMaxHealth = 200.0;
            }
        }
        instance.configVersion = CURRENT_VERSION;
        instance.sanitize();
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (Exception e) {
            GiantRefresh.LOGGER.warn("Could not write {}: {}", file, e.toString());
        }
    }

    private void sanitize() {
        giantMaxHealth = clamp(giantMaxHealth, 20, 4096);
        giantPunchDamage = clamp(giantPunchDamage, 0, 200);
        giantStompDamage = clamp(giantStompDamage, 0, 200);
        giantGrabDamage = clamp(giantGrabDamage, 0, 200);
        giantThrowStrength = clamp(giantThrowStrength, 0, 4);
        giantMovementSpeed = clamp(giantMovementSpeed, 0.05, 1.0);
        giantFollowRange = clamp(giantFollowRange, 8, 128);
        footprintChance = clamp(footprintChance, 0, 1);
        spawnAttemptInterval = (int) clamp(spawnAttemptInterval, 20, 72000);
        spawnChance = clamp(spawnChance, 0, 1);
        minSpawnDistance = (int) clamp(minSpawnDistance, 24, 128);
        maxSpawnDistance = (int) clamp(maxSpawnDistance, minSpawnDistance + 1, 160);
        giantExclusionRadius = (int) clamp(giantExclusionRadius, 0, 1024);
        hatChance = clamp(hatChance, 0, 1);
        ridersChance = clamp(ridersChance, 0, 1);
        hordeChance = clamp(hordeChance, 0, 1);
        trailRuinChance = clamp(trailRuinChance, 0, 1);
        screenShakeStrength = clamp(screenShakeStrength, 0, 3);
        growthScaleBonus = clamp(growthScaleBonus, 0.1, 4);
        growthSpeedPenalty = clamp(growthSpeedPenalty, 0, 0.9);
        growthDamageBonus = clamp(growthDamageBonus, 0, 5);
        shrinkingScale = clamp(shrinkingScale, 0.1, 0.95);
        shrinkingDamagePenalty = clamp(shrinkingDamagePenalty, 0, 0.95);
    }

    private static double clamp(double v, double min, double max) {
        return Double.isNaN(v) ? min : Math.max(min, Math.min(max, v));
    }
}
