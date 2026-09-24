package com.createbrewery;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue FERMENTATION_DURATION_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_CANS;
    public static final ModConfigSpec.BooleanValue ENABLE_DRUGS;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.DoubleValue SCREEN_EFFECTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        FERMENTATION_DURATION_MULTIPLIER = builder
            .comment("Scales all fermentation times. 1.0 = one in-game day for ale.")
            .defineInRange("fermentationDurationMultiplier", 1.0, 0.01, 100.0);
        ENABLE_CANS = builder
            .comment("Enable metal cans as an alternative to glass bottles.")
            .define("enableCans", true);
        ENABLE_DRUGS = builder
            .comment("Enable Koks and Keta. When off, the items exist but cannot be used.")
            .define("enableDrugs", true);
        SPEC = builder.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        SCREEN_EFFECTS = client
            .comment("Strength of the drunk screen effects (shader, camera sway, screen overlays).",
                "1.0 = full, 0.0 = off. Lower this if the effects are uncomfortable or you are sensitive to motion or flicker.",
                "Gameplay effects (weaving walk, aim drift, blackout) are not affected.")
            .defineInRange("drunkScreenEffects", 1.0, 0.0, 1.0);
        CLIENT_SPEC = client.build();
    }
}
