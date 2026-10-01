package com.createbrewery.drunk;

/** The PA's level plan. Free of Minecraft classes. */
final class PaLevel {
    private PaLevel() {}

    /** Speakers of one band this close to the listener (blocks) add up; further ones are not counted. */
    static final double STACK_RADIUS = 6.0;

    /** The gain each of {@code near} speakers of one band plays at: coherent copies of one signal add up, so each backs off by the square root. */
    static float stackGain(int near) {
        return near <= 1 ? 1f : (float) (1 / Math.sqrt(near));
    }
}
