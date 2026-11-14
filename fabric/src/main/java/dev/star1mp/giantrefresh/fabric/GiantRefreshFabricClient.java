package dev.star1mp.giantrefresh.fabric;

import dev.star1mp.giantrefresh.client.GRModelLayers;
import dev.star1mp.giantrefresh.client.GiantHarnessModel;
import dev.star1mp.giantrefresh.client.GiantModel;
import dev.star1mp.giantrefresh.client.GiantRenderer;
import dev.star1mp.giantrefresh.registry.GRContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
//#if MC >= 26.1
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
//#else
//$$ import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
//#endif

public class GiantRefreshFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        //#if MC >= 26.1
        ModelLayerRegistry.registerModelLayer(GRModelLayers.GIANT, GiantModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(GRModelLayers.GIANT_HARNESS, GiantHarnessModel::createLayer);
        //#else
        //$$ EntityModelLayerRegistry.registerModelLayer(GRModelLayers.GIANT, GiantModel::createBodyLayer);
        //$$ EntityModelLayerRegistry.registerModelLayer(GRModelLayers.GIANT_HARNESS, GiantHarnessModel::createLayer);
        //#endif
        EntityRendererRegistry.register(GRContent.GIANT.get(), GiantRenderer::new);
    }
}
