package com.alkeari.modtrix.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.alkeari.modtrix.api.ModConfig;
import com.alkeari.modtrix.impl.client.ConfigScreen;

/**
 * Mod Menu's entrypoint, added to the mod's fabric.mod.json by the build. Mod Menu shows the settings button only when the
 * factory returns a screen, so a mod that never builds a ModConfig shows none.
 */
public final class FabricModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ModConfig config = ModConfig.only();
            return config == null ? null : new ConfigScreen(parent, config);
        };
    }
}
