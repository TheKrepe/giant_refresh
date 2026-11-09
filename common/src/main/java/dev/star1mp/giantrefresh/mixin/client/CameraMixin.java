package dev.star1mp.giantrefresh.mixin.client;

import dev.star1mp.giantrefresh.client.GiantClientHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Light camera shake from nearby Giant foot-falls and stomps. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float xRot();

    @Shadow
    public abstract float yRot();

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void giantrefresh$shake(DeltaTracker deltaTracker, CallbackInfo ci) {
        float[] shake = GiantClientHooks.cameraShake();
        if (shake != null) {
            setRotation(yRot() + shake[0], xRot() + shake[1]);
        }
    }
}
