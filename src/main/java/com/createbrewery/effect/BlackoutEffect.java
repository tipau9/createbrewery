package com.createbrewery.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Filmriss: the player passes out. No tick logic of its own - the client blacks out the screen
 * and ignores movement input for as long as it is active, and DrunkServer reports the gap in
 * memory when it wears off.
 */
public class BlackoutEffect extends MobEffect {
    public BlackoutEffect() {
        super(MobEffectCategory.HARMFUL, 0x1A1030);
    }
}
