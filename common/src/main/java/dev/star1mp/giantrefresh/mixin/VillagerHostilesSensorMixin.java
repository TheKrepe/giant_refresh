package dev.star1mp.giantrefresh.mixin;

import dev.star1mp.giantrefresh.config.GRConfig;
import dev.star1mp.giantrefresh.entity.GiantEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villagers treat an awake Giant as a hostile and panic / run home, from a long way off. */
@Mixin(VillagerHostilesSensor.class)
public abstract class VillagerHostilesSensorMixin {
    @Inject(method = "isMatchingEntity", at = @At("HEAD"), cancellable = true)
    private void giantrefresh$fearGiants(ServerLevel level, LivingEntity villager, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof GiantEntity giant && GRConfig.get().villagersFleeGiants) {
            cir.setReturnValue(!giant.isGiantSleeping() && villager.distanceToSqr(giant) < 24.0 * 24.0);
        }
    }
}
