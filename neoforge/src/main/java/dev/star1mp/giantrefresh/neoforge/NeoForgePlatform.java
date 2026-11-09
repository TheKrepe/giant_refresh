package dev.star1mp.giantrefresh.neoforge;

import dev.star1mp.giantrefresh.platform.Platform;
import java.nio.file.Path;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.loading.FMLPaths;

public class NeoForgePlatform implements Platform {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0);
    }

    @Override
    public String loaderName() {
        return "NeoForge";
    }
}
