package dev.star1mp.giantrefresh.mixin.client;

import dev.star1mp.giantrefresh.cinema.Cinema;
import dev.star1mp.giantrefresh.client.CinemaClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Trailer recording only: logs every sound for the sound mix. */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @Inject(method = "play", at = @At("HEAD"))
    private void giantrefresh$logSound(SoundInstance sound, CallbackInfoReturnable<?> cir) {
        if (Cinema.ENABLED) {
            CinemaClient.onSound((SoundManager) (Object) this, sound);
        }
    }
}
