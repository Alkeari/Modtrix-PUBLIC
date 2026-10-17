package com.alkeari.modtrix.impl;

import com.alkeari.modtrix.api.ModConfig;

/** What each loader implements for the game client only; never loaded on a dedicated server. */
public interface ClientHooks {
    void onClientTick(Runnable listener);

    /** Adds the client receiver for a server-to-client message; called only in the game client. */
    <T> void registerReceiver(MessageSpec<T> spec);

    <T> void sendToServer(MessageSpec<T> spec, T message);

    /** Offers the settings screen where this loader lists mods: Mod Menu on Fabric, the Config button on Forge and NeoForge. */
    void registerConfigScreen(ModConfig config);
}
