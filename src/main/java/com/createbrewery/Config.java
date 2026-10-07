package com.createbrewery;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue FERMENTATION_DURATION_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_CANS;
    public static final ModConfigSpec.BooleanValue ENABLE_DRUGS;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.DoubleValue SCREEN_EFFECTS;
    public static final ModConfigSpec.BooleanValue RECORD_MUSIC;
    public static final ModConfigSpec.BooleanValue ENABLE_VEIL_LIGHTS;

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
        RECORD_MUSIC = client
            .comment("Debug: write what the MDMA beat and drop detection hears of every song to logs/brewery-traces/*.csv.",
                "One line per 20 ms of the song. Leave off unless you are tuning drop detection.")
            .define("recordMusicTraces", false);
        ENABLE_VEIL_LIGHTS = client
            .comment("Enable dynamic room lighting through Veil for club fixtures, strobes and drugs.",
                "Off by default: Veil's voxel shadow texture used to crash Nvidia OpenGL drivers (nvoglv64.dll glTexImage3D);",
                "Create Brewery now guards that upload. Turn it off again if your game crashes with Veil lights on.")
            .define("enableVeilLights", false);
        CLIENT_SPEC = client.build();
    }

    /** Nvidia too: VoxelShadowGridMixin keeps Veil's shadow texture upload from crashing its driver. */
    public static boolean areVeilLightsEnabled() {
        return ENABLE_VEIL_LIGHTS.get();
    }
}
