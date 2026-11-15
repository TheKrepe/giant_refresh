package dev.star1mp.giantrefresh.effect;

import dev.star1mp.giantrefresh.GiantRefresh;
import dev.star1mp.giantrefresh.config.GRConfig;
import dev.star1mp.giantrefresh.registry.GRContent;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Smoothly changes the size of an entity under Growth or Shrinking.
 *
 * <p>The size bonus follows its target like a critically damped spring: it starts gently,
 * speeds up and settles softly, with no jumps — also when the effect runs out, is cured with
 * milk, or one effect replaces the other. On the client the per-tick steps are additionally
 * interpolated between frames (see the render-state mixin).
 */
public final class GrowthHandler {
    private static final Identifier SCALE_ID = GiantRefresh.id("growth_scale");
    private static final Identifier SPEED_ID = GiantRefresh.id("growth_speed");
    private static final Identifier DAMAGE_ID = GiantRefresh.id("growth_damage");
    private static final Identifier REACH_ID = GiantRefresh.id("growth_reach");
    private static final Identifier BLOCK_REACH_ID = GiantRefresh.id("growth_block_reach");
    private static final Identifier STEP_ID = GiantRefresh.id("growth_step");

    /** Ticks over which the size eases back down before an effect runs out. */
    private static final int FADE_TICKS = 60;
    /** Spring stiffness (1/s): ~95% of a size change is done after about 4.7 / OMEGA seconds. */
    private static final double OMEGA = 1.9;
    private static final double DT = 0.05;

    /** Current rate of change of the size bonus per entity (server side only). */
    private static final Map<LivingEntity, Double> VELOCITY = new WeakHashMap<>();

    private GrowthHandler() {}

    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        AttributeInstance scale = entity.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        MobEffectInstance growth = entity.getEffect(GRContent.growthHolder());
        MobEffectInstance shrink = entity.getEffect(GRContent.shrinkingHolder());
        AttributeModifier existing = scale.getModifier(SCALE_ID);
        if (growth == null && shrink == null && existing == null) {
            return; // fast path for the 99.9% case
        }

        GRConfig cfg = GRConfig.get();
        double growMax = cfg.growthScaleBonus;
        double shrinkMax = 1.0 - cfg.shrinkingScale;
        // both effects at once cancel each other out (normal size); when one of them runs out,
        // the other one takes over again
        double g = fade(growth);
        double sh = fade(shrink);
        double target = growMax * g * (1.0 - sh) - shrinkMax * sh * (1.0 - g);
        double current = existing != null ? existing.amount() : 0.0;

        double next;
        if (existing == null && entity.tickCount <= 3) {
            // just loaded / respawned (modifiers are not saved): restore the size at once
            next = target;
            VELOCITY.remove(entity);
        } else {
            double v = VELOCITY.getOrDefault(entity, 0.0);
            double d = target - current;
            v += (OMEGA * OMEGA * d - 2.0 * OMEGA * v) * DT;
            next = current + v * DT;
            if (Math.abs(target - next) < 2.0E-3 && Math.abs(v) < 2.0E-2) {
                next = target;
                v = 0.0;
            }
            if (v == 0.0) {
                VELOCITY.remove(entity);
            } else {
                VELOCITY.put(entity, v);
            }
        }

        if (existing != null && Math.abs(next - current) < 1.0E-6) {
            return;
        }
        if (Math.abs(next) <= 1.0E-4 && growth == null && shrink == null) {
            remove(entity);
            VELOCITY.remove(entity);
            return;
        }
        apply(entity, Attributes.SCALE, SCALE_ID, next, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (next >= 0.0) {
            double p = growMax > 0 ? next / growMax : 0.0;
            apply(entity, Attributes.MOVEMENT_SPEED, SPEED_ID, -cfg.growthSpeedPenalty * p, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            apply(entity, Attributes.ATTACK_DAMAGE, DAMAGE_ID, cfg.growthDamageBonus * p, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            apply(entity, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, 1.5 * p, AttributeModifier.Operation.ADD_VALUE);
            apply(entity, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, 1.5 * p, AttributeModifier.Operation.ADD_VALUE);
            apply(entity, Attributes.STEP_HEIGHT, STEP_ID, 0.6 * p, AttributeModifier.Operation.ADD_VALUE);
        } else {
            double p = shrinkMax > 0 ? -next / shrinkMax : 0.0;
            apply(entity, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            apply(entity, Attributes.ATTACK_DAMAGE, DAMAGE_ID, -cfg.shrinkingDamagePenalty * p, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            apply(entity, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, 0.0, AttributeModifier.Operation.ADD_VALUE);
            apply(entity, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, 0.0, AttributeModifier.Operation.ADD_VALUE);
            apply(entity, Attributes.STEP_HEIGHT, STEP_ID, 0.0, AttributeModifier.Operation.ADD_VALUE);
        }
    }

    /** 1 while the effect lasts, easing to 0 over its last {@link #FADE_TICKS} ticks; 0 without it. */
    private static double fade(MobEffectInstance instance) {
        if (instance == null) {
            return 0.0;
        }
        if (instance.isInfiniteDuration() || instance.getDuration() >= FADE_TICKS) {
            return 1.0;
        }
        return instance.getDuration() / (double) FADE_TICKS;
    }

    /** Giants are far too big (and too stubborn) for these potions. */
    public static boolean isSizeEffect(Holder<MobEffect> effect) {
        return effect.equals(GRContent.growthHolder()) || effect.equals(GRContent.shrinkingHolder());
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
