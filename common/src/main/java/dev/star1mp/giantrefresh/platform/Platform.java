package dev.star1mp.giantrefresh.platform;

import java.nio.file.Path;
import net.minecraft.world.item.CreativeModeTab;

/**
 * The small set of things that genuinely differ per loader.
 * Each loader installs its implementation with {@link #set} before calling {@code GiantRefresh.init()}.
 */
public interface Platform {
    Path configDir();

    CreativeModeTab.Builder creativeTabBuilder();

    String loaderName();

    static Platform get() {
        Platform p = Holder.instance;
        if (p == null) {
            throw new IllegalStateException("Giant Refresh: platform not initialised");
        }
        return p;
    }

    static void set(Platform platform) {
        Holder.instance = platform;
    }

    final class Holder {
        private static volatile Platform instance;

        private Holder() {}
    }
}
