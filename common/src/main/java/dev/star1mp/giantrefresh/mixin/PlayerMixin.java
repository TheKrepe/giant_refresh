package dev.star1mp.giantrefresh.mixin;

import dev.star1mp.giantrefresh.entity.GiantEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player caught in a Giant's fist cannot simply sneak out of it. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void giantrefresh$heldByGiant(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (self.getVehicle() instanceof GiantEntity giant && giant.isHeld(self)) {
            cir.setReturnValue(false);
        }
    }
}
