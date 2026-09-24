package com.createbrewery.effect;

import com.createbrewery.CreateBrewery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Bierlaune: the upside of one or two beers. A bit of liquid courage (harder hits), a lucky
 * streak, and the warmth that keeps the cold out. DrunkServer keeps it up only while the blood
 * level is in the party zone; the dance notes on jumping are there too.
 */
public class GoodMoodEffect extends MobEffect {
    public GoodMoodEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xF2B233);
        addAttributeModifier(Attributes.ATTACK_DAMAGE,
            ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "effect.good_mood.courage"),
            0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.LUCK,
            ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "effect.good_mood.luck"),
            1.0, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Beer warms you up: powder snow cannot freeze you while the mood lasts.
        if (!entity.level().isClientSide && entity.getTicksFrozen() > 0) entity.setTicksFrozen(0);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}
