package com.alkeari.modtrix.neoforge;

import com.alkeari.modtrix.api.ModConfig;
import com.alkeari.modtrix.impl.client.ConfigScreen;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** From NeoForge 20.5 the Config button reads an IConfigScreenFactory registered on the mod's own container. */
final class NeoForgeConfigScreens {
    private NeoForgeConfigScreens() {
    }

    static void register(ModConfig config) {
        ModLoadingContext.get().getActiveContainer()
                .registerExtensionPoint(IConfigScreenFactory.class, (container, parent) -> new ConfigScreen(parent, config));
    }
}
