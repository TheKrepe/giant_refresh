package dev.star1mp.giantrefresh.neoforge;

import dev.star1mp.giantrefresh.GiantRefresh;
import dev.star1mp.giantrefresh.entity.GiantEntity;
import dev.star1mp.giantrefresh.entity.GiantEvents;
import dev.star1mp.giantrefresh.platform.Platform;
import dev.star1mp.giantrefresh.registry.GRContent;
import dev.star1mp.giantrefresh.registry.RegEntry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(GiantRefresh.MOD_ID)
public class GiantRefreshNeoForge {
    public GiantRefreshNeoForge(IEventBus modBus, ModContainer container) {
        Platform.set(new NeoForgePlatform());
        GiantRefresh.init();

        modBus.addListener(GiantRefreshNeoForge::onRegister);
        modBus.addListener(GiantRefreshNeoForge::onAttributes);
        NeoForge.EVENT_BUS.addListener(GiantRefreshNeoForge::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(GiantRefreshNeoForge::onLevelTick);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onRegister(RegisterEvent event) {
        for (ResourceKey<? extends Registry<?>> key : GRContent.ORDER) {
            if (!event.getRegistryKey().equals(key)) {
                continue;
            }
            event.register((ResourceKey) key, helper -> {
                for (RegEntry<?> entry : GRContent.entries(key)) {
                    ((RegisterEvent.RegisterHelper) helper).register(entry.id(), entry.get());
                }
            });
        }
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(GRContent.GIANT.get(), GiantEntity.createAttributes().build());
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GiantEvents.onEntityJoin(event.getEntity(), level);
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GiantEvents.onLevelTick(level);
        }
    }
}
