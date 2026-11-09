package dev.star1mp.giantrefresh.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Growth: the size change itself is driven smoothly by {@link GrowthHandler} every tick,
 * so the effect class only defines identity, colour and category.
 */
public class GrowthEffect extends MobEffect {
    public GrowthEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8DB360);
    }
}
