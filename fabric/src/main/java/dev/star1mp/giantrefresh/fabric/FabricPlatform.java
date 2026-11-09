package dev.star1mp.giantrefresh.fabric;

import dev.star1mp.giantrefresh.platform.Platform;
import java.nio.file.Path;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.CreativeModeTab;

public class FabricPlatform implements Platform {
    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return FabricCreativeModeTab.builder();
    }

    @Override
    public String loaderName() {
        return "Fabric";
    }
}
