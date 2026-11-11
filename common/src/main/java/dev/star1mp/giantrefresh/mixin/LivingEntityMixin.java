package dev.star1mp.giantrefresh.mixin;

import dev.star1mp.giantrefresh.effect.GrowthHandler;
import dev.star1mp.giantrefresh.effect.SmoothScale;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives the smooth size change of the Growth / Shrinking potions and keeps the scale history for rendering. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements SmoothScale {
    @Unique
    private float giantrefresh$scaleO = -1.0F;
    @Unique
    private float giantrefresh$scale = -1.0F;

    @Inject(method = "tick", at = @At("HEAD"))
    private void giantrefresh$rememberScale(CallbackInfo ci) {
        float now = ((LivingEntity) (Object) this).getScale();
        giantrefresh$scaleO = giantrefresh$scale < 0.0F ? now : giantrefresh$scale;
        giantrefresh$scale = now;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void giantrefresh$tickGrowth(CallbackInfo ci) {
        GrowthHandler.tick((LivingEntity) (Object) this);
    }

    @Override
    public float giantrefresh$renderScale(float partialTick) {
        float now = ((LivingEntity) (Object) this).getScale();
        if (giantrefresh$scale < 0.0F || Math.abs(now - giantrefresh$scale) > 1.0E-4F) {
            return now; // changed outside the tick history (or no history yet): show as is
        }
        return Mth.lerp(partialTick, giantrefresh$scaleO, giantrefresh$scale);
    }
}
