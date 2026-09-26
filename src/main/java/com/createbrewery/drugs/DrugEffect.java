package com.createbrewery.drugs;

import com.createbrewery.effect.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A drug effect with a real time course: it comes up after the dose, holds, and fades (see
 * {@link Pharmacology#strength}), and its attribute changes follow that curve instead of
 * switching on at full strength. Plus an optional server-side tick every {@code interval} ticks.
 * The client derives all of its visuals from these effects and {@link #strength}, so the effects
 * themselves stay small.
 */
public class DrugEffect extends MobEffect {
    private record Scaled(Holder<Attribute> attribute, ResourceLocation id, double full) {}

    private final int interval;
    private final BiConsumer<LivingEntity, Integer> serverTick;
    /** Full duration of one dose (0: none, it only fades), come-up and fade in ticks. */
    private final int total, onset, fade;
    private final List<Scaled> scaled = new ArrayList<>();
    /** Whether doses stack (lines, joints); single events like the K-Loch always act in full. */
    private boolean stacking;
    /** Amplifier steps that make one dose: 1 for a line, a joint's hits for weed. */
    private int perDose = 1;

    public DrugEffect(MobEffectCategory category, int colour, int interval, BiConsumer<LivingEntity, Integer> serverTick,
                      int total, int onset, int fade) {
        super(category, colour);
        this.interval = interval;
        this.serverTick = serverTick;
        this.total = total;
        this.onset = onset;
        this.fade = fade;
    }

    /** An attribute change of {@code full} (ADD_MULTIPLIED_TOTAL) at the peak of the strongest dose. */
    public DrugEffect scaled(Holder<Attribute> attribute, ResourceLocation id, double full) {
        scaled.add(new Scaled(attribute, id, full));
        return this;
    }

    /** Doses stack up, each adding less (see {@link Pharmacology#dosesFelt}). */
    public DrugEffect stacks() {
        return stacks(1);
    }

    /** Doses stack, and every {@code perDose} amplifier steps make one full dose (hits of a joint). */
    public DrugEffect stacks(int perDose) {
        stacking = true;
        this.perDose = perDose;
        return this;
    }

    public int total() { return total; }
    public int onset() { return onset; }
    public int fade() { return fade; }

    /** 0..1 how strongly {@code effect} acts on {@code entity} right now; 0 if absent. Works on both sides. */
    public static float strength(LivingEntity entity, Holder<MobEffect> effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        if (instance == null) return 0f;
        // Naloxon sits on the receptors: the heroin is still there, but does nothing until it wears off.
        if (effect.is(ModEffects.NOD.getKey()) && entity.hasEffect(ModEffects.NALOXONE)) return 0f;
        if (!(effect.value() instanceof DrugEffect drug)) return 1f;
        int elapsed = drug.total - instance.getDuration();
        float strength = Pharmacology.strength(elapsed, instance.getDuration(), drug.onset, drug.fade, instance.getAmplifier() > 0);
        // Mushrooms come in waves: stronger, then easing off, every two minutes or so.
        if (effect.is(ModEffects.SHROOM_TRIP.getKey())) strength *= Pharmacology.shroomWave(elapsed);
        return strength;
    }

    /** Strength times how strong the stacked doses feel (0..1). */
    public static float felt(LivingEntity entity, Holder<MobEffect> effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        if (instance == null) return 0f;
        if (!(effect.value() instanceof DrugEffect drug) || !drug.stacking) return strength(entity, effect);
        float felt = strength(entity, effect) * Pharmacology.feltFor((instance.getAmplifier() + 1) / (float) drug.perDose);
        // Used to heroin, the high wears thin: the same shot does less and less.
        MobEffectInstance habit = entity.getEffect(ModEffects.OPIOID_HABIT);
        if (habit != null && effect.is(ModEffects.NOD.getKey())) felt *= 1f - 0.2f * (habit.getAmplifier() + 1);
        return felt;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Server only: the client runs effect ticks too, and random rolls there would disagree.
        if (entity.level().isClientSide) return true;
        MobEffectInstance instance = instanceOn(entity);
        if (instance == null) return true;
        int duration = instance.getDuration();
        if (duration % 10 == 0 && !scaled.isEmpty()) {
            float felt = felt(entity, instance.getEffect());
            for (Scaled s : scaled) {
                AttributeInstance attribute = entity.getAttribute(s.attribute());
                if (attribute != null) {
                    attribute.addOrUpdateTransientModifier(new AttributeModifier(s.id(), s.full() * felt,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            }
        }
        if (serverTick != null && interval > 0 && duration % interval == 0) serverTick.accept(entity, amplifier);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return !scaled.isEmpty() || serverTick != null;
    }

    /** Called when the effect ends (expired, milk, death) and before a new dose updates it. */
    @Override
    public void removeAttributeModifiers(AttributeMap attributes) {
        super.removeAttributeModifiers(attributes);
        for (Scaled s : scaled) {
            AttributeInstance attribute = attributes.getInstance(s.attribute());
            if (attribute != null) attribute.removeModifier(s.id());
        }
    }

    private MobEffectInstance instanceOn(LivingEntity entity) {
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            if (instance.getEffect().value() == this) return instance;
        }
        return null;
    }
}
