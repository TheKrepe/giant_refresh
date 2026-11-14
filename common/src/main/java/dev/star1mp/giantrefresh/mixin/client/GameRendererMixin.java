package dev.star1mp.giantrefresh.mixin.client;

import dev.star1mp.giantrefresh.client.GiantClientHooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CI render test only: takes the pending test screenshot once a frame is complete. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void giantrefresh$afterFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (GiantClientHooks.CLIENT_TEST) {
            GiantClientHooks.afterFrame();
        }
    }
}
