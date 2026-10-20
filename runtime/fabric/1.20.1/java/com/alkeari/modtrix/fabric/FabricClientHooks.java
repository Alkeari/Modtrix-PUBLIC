package com.alkeari.modtrix.fabric;

import com.alkeari.modtrix.api.ModConfig;
import com.alkeari.modtrix.impl.ClientHooks;
import com.alkeari.modtrix.impl.MessageSpec;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class FabricClientHooks implements ClientHooks {
    @Override
    public void onClientTick(Runnable listener) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> listener.run());
    }

    @Override
    public <T> void registerReceiver(MessageSpec<T> spec) {
        FabricClientNetwork.registerReceiver(spec);
    }

    @Override
    public <T> void sendToServer(MessageSpec<T> spec, T message) {
        FabricClientNetwork.sendToServer(spec, message);
    }

    @Override
    public void registerConfigScreen(ModConfig config) {
        // Mod Menu asks FabricModMenu for the screen, which finds the config through ModConfig.only.
    }
}
