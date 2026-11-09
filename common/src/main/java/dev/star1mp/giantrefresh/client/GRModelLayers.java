package dev.star1mp.giantrefresh.client;

import dev.star1mp.giantrefresh.GiantRefresh;
import net.minecraft.client.model.geom.ModelLayerLocation;

public final class GRModelLayers {
    public static final ModelLayerLocation GIANT = new ModelLayerLocation(GiantRefresh.id("giant"), "main");
    public static final ModelLayerLocation GIANT_HARNESS = new ModelLayerLocation(GiantRefresh.id("giant"), "harness");

    private GRModelLayers() {}
}
