package dev.star1mp.giantrefresh.fabric;

import dev.star1mp.giantrefresh.GiantRefresh;
import dev.star1mp.giantrefresh.entity.GiantEntity;
import dev.star1mp.giantrefresh.entity.GiantEvents;
import dev.star1mp.giantrefresh.platform.Platform;
import dev.star1mp.giantrefresh.registry.GRContent;
import dev.star1mp.giantrefresh.registry.RegEntry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
//#if MC >= 26.1
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
//#else
//$$ import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
//#endif
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;

public class GiantRefreshFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Platform.set(new FabricPlatform());
        GiantRefresh.init();
        registerAll();

        FabricDefaultAttributeRegistry.register(GRContent.GIANT.get(), GiantEntity.createAttributes());

        for (var tab : java.util.List.of(GRContent.TAB_SPAWN_EGGS, GRContent.TAB_INGREDIENTS, GRContent.TAB_FUNCTIONAL_BLOCKS)) {
            //#if MC >= 26.1
            CreativeModeTabEvents.modifyOutputEvent(tab).register(output ->
                    GRContent.fillCreativeTab(tab, (anchor, stack) -> output.insertAfter(anchor, stack)));
            //#else
            //$$ ItemGroupEvents.modifyEntriesEvent(tab).register(entries ->
            //$$         GRContent.fillCreativeTab(tab, (anchor, stack) -> entries.addAfter(anchor, stack)));
            //#endif
        }
        //#if MC < 26.1
        //$$ net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder.BUILD.register(GRContent::registerBrewing);
        //#elif MC < 26.3
        //$$ net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder.BUILD.register(GRContent::registerBrewing);
        //#endif

        ServerEntityEvents.ENTITY_LOAD.register(GiantEvents::onEntityJoin);
        //#if MC >= 26.1
        ServerTickEvents.END_LEVEL_TICK.register(GiantEvents::onLevelTick);
        //#else
        //$$ ServerTickEvents.END_WORLD_TICK.register(GiantEvents::onLevelTick);
        //#endif

        BiomeModifications.addFeature(BiomeSelectors.tag(GRContent.GIANT_BIOMES), GenerationStep.Decoration.VEGETAL_DECORATION,
                GRContent.key(Registries.PLACED_FEATURE, "giant_footprints"));
        BiomeModifications.addFeature(BiomeSelectors.tag(GRContent.GIANT_BIOMES), GenerationStep.Decoration.VEGETAL_DECORATION,
                GRContent.key(Registries.PLACED_FEATURE, "giant_camp"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerAll() {
        for (ResourceKey<? extends Registry<?>> key : GRContent.ORDER) {
            Registry registry = BuiltInRegistries.REGISTRY.getValue(key.identifier());
            if (registry == null) {
                throw new IllegalStateException("Giant Refresh: missing registry " + key.identifier());
            }
            for (RegEntry<?> entry : GRContent.entries(key)) {
                Registry.register(registry, entry.id(), entry.get());
            }
        }
    }
}
