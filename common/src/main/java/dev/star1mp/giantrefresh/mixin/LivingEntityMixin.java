package dev.star1mp.giantrefresh.mixin;

import dev.star1mp.giantrefresh.effect.GrowthHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives the smooth size change of the Potion of Growth. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void giantrefresh$tickGrowth(CallbackInfo ci) {
        GrowthHandler.tick((LivingEntity) (Object) this);
    }
}
