package com.alkeari.modtrix.api;

import com.alkeari.modtrix.impl.UpdateCheck;
import com.alkeari.modtrix.impl.client.UpdateNotice;

/**
 * The update check for a mod that keeps its settings outside {@link ModConfig}: call {@link #check} during mod
 * initialization only while the mod's own update setting is on. A mod using {@link ModConfig} calls
 * {@link ModConfig.Builder#checkForUpdates} instead, which adds that setting itself.
 */
public final class Updates {
    private Updates() {
    }

    /**
     * Reads {@code url} once, off the game thread, and only when it lists a newer version of {@code modId} for the
     * running Minecraft version and loader does the mod log it and tell the player in chat on joining a world. The file
     * format is described on {@link ModConfig.Builder#checkForUpdates}; nothing is sent but the request for it.
     */
    public static void check(String modId, String url) {
        UpdateCheck.start(modId, url);
        Events.onClientTick(() -> UpdateNotice.tick());
    }
}
