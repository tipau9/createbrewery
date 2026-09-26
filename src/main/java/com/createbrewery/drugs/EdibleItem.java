package com.createbrewery.drugs;

import com.createbrewery.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Space-Brownie: weed you eat. Nothing for a long while (the liver has to turn it into
 * 11-OH-THC first), then far stronger than a joint. The classic mistake: "I feel nothing" -
 * and a second one. See {@link DrugServer#edibleKicksIn}.
 */
public class EdibleItem extends Item {
    /** About 75 s before it kicks in (in real life an hour or two). */
    public static final int DELAY = 1500;

    public EdibleItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && DrugServer.enabled()) {
            MobEffectInstance before = entity.getEffect(ModEffects.EDIBLE_PENDING);
            // A second one while the first still waits: both come on together.
            entity.addEffect(new MobEffectInstance(ModEffects.EDIBLE_PENDING, before == null ? DELAY : before.getDuration(),
                before == null ? 0 : before.getAmplifier() + 1, false, false, false));
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
