package com.terraformersmc.modmenu.api;

/** Compile-time stand-in for Mod Menu's interface; Mod Menu provides the real one at runtime and this never ships. */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> null;
    }
}
