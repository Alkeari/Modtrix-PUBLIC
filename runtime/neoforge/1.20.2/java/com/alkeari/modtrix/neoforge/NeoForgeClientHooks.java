package com.alkeari.modtrix.neoforge;

import com.alkeari.modtrix.api.ModConfig;
import com.alkeari.modtrix.impl.ClientHooks;
import com.alkeari.modtrix.impl.MessageSpec;

public final class NeoForgeClientHooks implements ClientHooks {
    @Override
    public void onClientTick(Runnable listener) {
        NeoForgeClientTicks.onClientTick(listener);
    }

    @Override
    public <T> void registerReceiver(MessageSpec<T> spec) {
        // The payload registration in NeoForgeNetwork already carries the client handler.
    }

    @Override
    public <T> void sendToServer(MessageSpec<T> spec, T message) {
        NeoForgeNetwork.sendToServer(spec, message);
    }

    @Override
    public void registerConfigScreen(ModConfig config) {
        NeoForgeConfigScreens.register(config);
    }
}
