package com.alkeari.modtrix.fabric;

import com.alkeari.modtrix.impl.MessageSpec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

final class FabricClientNetwork {
    private FabricClientNetwork() {
    }

    static <T> void registerReceiver(MessageSpec<T> spec) {
        ClientPlayNetworking.registerGlobalReceiver(spec.id(), (client, handler, buf, sender) -> {
            T message = spec.decoder().apply(buf);
            client.execute(() -> spec.clientHandler().accept(message));
        });
    }

    static <T> void sendToServer(MessageSpec<T> spec, T message) {
        ClientPlayNetworking.send(spec.id(), FabricNetwork.encode(spec, message));
    }
}
