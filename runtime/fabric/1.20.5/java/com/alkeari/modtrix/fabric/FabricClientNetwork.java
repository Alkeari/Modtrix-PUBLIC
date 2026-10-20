package com.alkeari.modtrix.fabric;

import com.alkeari.modtrix.impl.MessageSpec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

final class FabricClientNetwork {
    private FabricClientNetwork() {
    }

    static <T> void registerReceiver(MessageSpec<T> spec) {
        ClientPlayNetworking.registerGlobalReceiver(FabricNetwork.type(spec),
                (payload, context) -> spec.clientHandler().accept(spec.type().cast(payload.value())));
    }

    static <T> void sendToServer(MessageSpec<T> spec, T message) {
        ClientPlayNetworking.send(FabricNetwork.wrap(spec, message));
    }
}
