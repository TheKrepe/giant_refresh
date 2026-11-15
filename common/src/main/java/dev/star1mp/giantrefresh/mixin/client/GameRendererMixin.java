package dev.star1mp.giantrefresh.mixin.client;

import dev.star1mp.giantrefresh.client.GiantClientHooks;
//#if MC < 26.3
//$$ import net.minecraft.client.DeltaTracker;
//#endif
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CI render test only: takes the pending test screenshot once a frame is complete. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    //#if MC >= 26.3
    private void giantrefresh$afterFrame(CallbackInfo ci) {
    //#else
    //$$ private void giantrefresh$afterFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
    //#endif
        if (dev.star1mp.giantrefresh.cinema.Cinema.ENABLED) {
            dev.star1mp.giantrefresh.client.CinemaClient.afterFrame();
        }
        if (GiantClientHooks.CLIENT_TEST || GiantClientHooks.SHOWCASE) {
            GiantClientHooks.afterFrame();
        }
    }
}
