package dev.star1mp.giantrefresh;

import dev.star1mp.giantrefresh.config.GRConfig;
import dev.star1mp.giantrefresh.platform.Platform;
import dev.star1mp.giantrefresh.registry.GRContent;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Giant Refresh — turns the forgotten Giant into a real inhabitant of the world.
 * Author: Star_1mp
 */
public final class GiantRefresh {
    public static final String MOD_ID = "giant_refresh";
    public static final Logger LOGGER = LoggerFactory.getLogger("Giant Refresh");

    private GiantRefresh() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Called by each loader as early as possible, before registration. */
    public static void init() {
        GRConfig.load(Platform.get().configDir());
        GRContent.bootstrap();
        LOGGER.info("Giant Refresh loaded — the Giants are waking up.");
    }
}
