package dev.star1mp.giantrefresh.mixin.client;

import dev.star1mp.giantrefresh.client.GiantClientHooks;
import net.minecraft.client.Camera;
//#if MC >= 26.1
import net.minecraft.client.DeltaTracker;
//#endif
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

    //#if MC >= 1.21.11
    @Shadow
    public abstract float xRot();

    @Shadow
    public abstract float yRot();
    //#else
    //$$ @Shadow
    //$$ public abstract float getXRot();
    //$$
    //$$ @Shadow
    //$$ public abstract float getYRot();
    //$$
    //$$ private float xRot() { return getXRot(); }
    //$$
    //$$ private float yRot() { return getYRot(); }
    //#endif

    //#if MC >= 26.1
    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void giantrefresh$shake(DeltaTracker deltaTracker, CallbackInfo ci) {
    //#elif MC >= 1.21.11
    //$$ @Inject(method = "setup", at = @At("TAIL"))
    //$$ private void giantrefresh$shake(net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
    //#else
    //$$ @Inject(method = "setup", at = @At("TAIL"))
    //$$ private void giantrefresh$shake(net.minecraft.world.level.BlockGetter level, net.minecraft.world.entity.Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
    //#endif
        float[] shake = GiantClientHooks.cameraShake();
        if (shake != null) {
            setRotation(yRot() + shake[0], xRot() + shake[1]);
        }
    }
}
