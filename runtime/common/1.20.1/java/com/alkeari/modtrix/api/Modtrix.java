package com.alkeari.modtrix.api;

import com.alkeari.modtrix.impl.Hooks;

/** Where the mod is running: which loader, which side, and which other mods are present. */
public final class Modtrix {
    private Modtrix() {
    }

    /** {@code fabric}, {@code forge} or {@code neoforge}. */
    public static String loader() {
        return Hooks.platform().loader();
    }

    /** True in the game client, including its integrated server; false on a dedicated server. */
    public static boolean isClient() {
        return Hooks.platform().isClient();
    }

    public static boolean isModLoaded(String modId) {
        return Hooks.platform().isModLoaded(modId);
    }

    /**
     * Runs {@code action} only in the game client; keep client classes inside it so a dedicated server never loads them.
     * Pass a lambda ({@code () -> MyClient.init()}), never a method reference to a client class: the reference resolves
     * that class wherever the call runs, and verifying it can load client-only Minecraft classes on a dedicated server.
     */
    public static void onClient(Runnable action) {
        if (isClient()) {
            action.run();
        }
    }
}
