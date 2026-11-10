package dev.star1mp.giantrefresh.effect;

import dev.star1mp.giantrefresh.GiantRefresh;
import dev.star1mp.giantrefresh.config.GRConfig;
import dev.star1mp.giantrefresh.registry.GRContent;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Smoothly grows / shrinks an entity under the Growth effect.
 * The scale is never switched instantly: it eases towards the target a little every tick,
 * also when the effect runs out or is removed with milk.
 */
public final class GrowthHandler {
    private static final Identifier SCALE_ID = GiantRefresh.id("growth_scale");
    private static final Identifier SPEED_ID = GiantRefresh.id("growth_speed");
    private static final Identifier DAMAGE_ID = GiantRefresh.id("growth_damage");
    private static final Identifier REACH_ID = GiantRefresh.id("growth_reach");
    private static final Identifier BLOCK_REACH_ID = GiantRefresh.id("growth_block_reach");
    private static final Identifier STEP_ID = GiantRefresh.id("growth_step");

    /** Ticks needed for a full grow / shrink. */
    private static final int TRANSITION_TICKS = 40;

    private GrowthHandler() {}

    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        AttributeInstance scale = entity.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        Holder<net.minecraft.world.effect.MobEffect> growth = GRContent.growthHolder();
        MobEffectInstance instance = entity.getEffect(growth);
        AttributeModifier existing = scale.getModifier(SCALE_ID);
        if (instance == null && existing == null) {
            return; // fast path for the 99.9% case
        }

        double max = GRConfig.get().growthScaleBonus;
        double current = existing != null ? existing.amount() : 0.0;
        double target = 0.0;
        if (instance != null) {
            target = max;
            if (!instance.isInfiniteDuration() && instance.getDuration() < TRANSITION_TICKS) {
                target = max * instance.getDuration() / (double) TRANSITION_TICKS;
            }
        }
        double step = max / TRANSITION_TICKS;
        double next = current < target ? Math.min(target, current + step) : Math.max(target, current - step);
        if (existing == null && instance != null && entity.tickCount <= 3) {
            next = target; // just loaded (modifiers are not saved): restore the size at once instead of re-growing
        }
        if (Math.abs(next - current) < 1.0E-6 && existing != null) {
            return;
        }
        if (next <= 1.0E-4) {
            remove(entity);
            return;
        }
        double progress = next / max;
        GRConfig cfg = GRConfig.get();
        apply(entity, Attributes.SCALE, SCALE_ID, next, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        apply(entity, Attributes.MOVEMENT_SPEED, SPEED_ID, -cfg.growthSpeedPenalty * progress, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        apply(entity, Attributes.ATTACK_DAMAGE, DAMAGE_ID, cfg.growthDamageBonus * progress, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        apply(entity, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, 1.5 * progress, AttributeModifier.Operation.ADD_VALUE);
        apply(entity, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, 1.5 * progress, AttributeModifier.Operation.ADD_VALUE);
        apply(entity, Attributes.STEP_HEIGHT, STEP_ID, 0.6 * progress, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void apply(LivingEntity entity, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = entity.getAttribute(attribute);
        if (inst != null) {
            inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    private static void remove(LivingEntity entity) {
        removeOne(entity, Attributes.SCALE, SCALE_ID);
        removeOne(entity, Attributes.MOVEMENT_SPEED, SPEED_ID);
        removeOne(entity, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
        removeOne(entity, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
        removeOne(entity, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID);
        removeOne(entity, Attributes.STEP_HEIGHT, STEP_ID);
    }

    private static void removeOne(LivingEntity entity, Holder<Attribute> attribute, Identifier id) {
        AttributeInstance inst = entity.getAttribute(attribute);
        if (inst != null) {
            inst.removeModifier(id);
        }
    }
}
