package dev.star1mp.giantrefresh.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Growth / Shrinking: the size change itself is driven smoothly by {@link GrowthHandler} every
 * tick, so the effect class only defines identity, colour and category.
 */
public class GrowthEffect extends MobEffect {
    public GrowthEffect() {
        this(MobEffectCategory.BENEFICIAL, 0x8DB360);
    }

    public GrowthEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
