package dev.star1mp.giantrefresh.fabric;

import dev.star1mp.giantrefresh.client.GRModelLayers;
import dev.star1mp.giantrefresh.client.GiantHarnessModel;
import dev.star1mp.giantrefresh.client.GiantModel;
import dev.star1mp.giantrefresh.client.GiantRenderer;
import dev.star1mp.giantrefresh.registry.GRContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

public class GiantRefreshFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(GRModelLayers.GIANT, GiantModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(GRModelLayers.GIANT_HARNESS, GiantHarnessModel::createLayer);
        EntityRendererRegistry.register(GRContent.GIANT.get(), GiantRenderer::new);
    }
}
