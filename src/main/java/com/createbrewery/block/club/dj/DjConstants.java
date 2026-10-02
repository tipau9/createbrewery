package com.createbrewery.block.club.dj;

public final class DjConstants {
    private DjConstants() {}

    public static final int W = 424;
    public static final int H = 248;

    public static final int[] DECK_COLORS = {
        0xFF00E5FF, // Deck 1: Cyan / Electric Blue
        0xFFFF9900, // Deck 2: Orange / Golden Amber
        0xFFA833FF, // Deck 3: Purple / Neon Violet
        0xFF00FF66  // Deck 4: Lime / Neon Green
    };

    public static final int[] PAD_COLORS = {
        0xFFFF3344, // Pad 1: Red
        0xFFFF7722, // Pad 2: Orange
        0xFFFFCC00, // Pad 3: Yellow
        0xFF00FF66, // Pad 4: Green
        0xFF00E5FF, // Pad 5: Cyan
        0xFF2288FF, // Pad 6: Blue
        0xFFA833FF, // Pad 7: Purple
        0xFFFF33AA  // Pad 8: Magenta
    };

    public static final String[] PAD_MODE_NAMES = {
        "HOT CUE", "BEAT LOOP", "SLIP LOOP", "BEAT JUMP"
    };
}
