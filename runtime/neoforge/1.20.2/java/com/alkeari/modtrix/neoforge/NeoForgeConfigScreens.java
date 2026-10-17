package com.alkeari.modtrix.neoforge;

import com.alkeari.modtrix.api.ModConfig;
import com.alkeari.modtrix.impl.client.ConfigScreen;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.ConfigScreenHandler;

/** NeoForge 20.4 still has Forge's ConfigScreenFactory extension point. */
final class NeoForgeConfigScreens {
    private NeoForgeConfigScreens() {
    }

    static void register(ModConfig config) {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ConfigScreen(parent, config)));
    }
}
