package dev.star1mp.giantrefresh.neoforge;

import dev.star1mp.giantrefresh.GiantRefresh;
import dev.star1mp.giantrefresh.client.GRModelLayers;
import dev.star1mp.giantrefresh.client.GiantHarnessModel;
import dev.star1mp.giantrefresh.client.GiantModel;
import dev.star1mp.giantrefresh.client.GiantRenderer;
import dev.star1mp.giantrefresh.registry.GRContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = GiantRefresh.MOD_ID, dist = Dist.CLIENT)
public class GiantRefreshNeoForgeClient {
    public GiantRefreshNeoForgeClient(IEventBus modBus) {
        modBus.addListener(GiantRefreshNeoForgeClient::onLayers);
        modBus.addListener(GiantRefreshNeoForgeClient::onRenderers);
    }

    private static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GRModelLayers.GIANT, GiantModel::createBodyLayer);
        event.registerLayerDefinition(GRModelLayers.GIANT_HARNESS, GiantHarnessModel::createLayer);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GRContent.GIANT.get(), GiantRenderer::new);
    }
}
