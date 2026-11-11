package dev.star1mp.giantrefresh.effect;

/**
 * Implemented on every LivingEntity (by mixin): remembers the scale of the last two ticks so the
 * renderer can blend size changes between frames instead of stepping once per tick.
 */
public interface SmoothScale {
    float giantrefresh$renderScale(float partialTick);
}
