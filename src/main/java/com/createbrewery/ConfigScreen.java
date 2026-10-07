package com.createbrewery;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client only: the Config button for Create Brewery in the mods list, NeoForge's own config screen. */
final class ConfigScreen {
    private ConfigScreen() {}

    static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
