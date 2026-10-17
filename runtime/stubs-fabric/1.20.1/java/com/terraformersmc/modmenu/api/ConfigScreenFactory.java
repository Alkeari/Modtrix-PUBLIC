package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

/** Compile-time stand-in for Mod Menu's interface; Mod Menu provides the real one at runtime and this never ships. */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {
    S create(Screen parent);
}
