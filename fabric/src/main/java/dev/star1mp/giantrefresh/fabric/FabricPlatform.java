package dev.star1mp.giantrefresh.fabric;

import dev.star1mp.giantrefresh.platform.Platform;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public class FabricPlatform implements Platform {
    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String loaderName() {
        return "Fabric";
    }
}
