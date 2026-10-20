package com.alkeari.modtrix.fabric;

import com.alkeari.modtrix.impl.MessageSpec;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Fabric networking before 1.20.5: raw buffers keyed by id, handlers called on the network thread. */
final class FabricNetwork {
    private FabricNetwork() {
    }

    static <T> void register(MessageSpec<T> spec) {
        if (spec.toClient()) {
            return;
        }
        ServerPlayNetworking.registerGlobalReceiver(spec.id(), (server, player, handler, buf, sender) -> {
            T message = spec.decoder().apply(buf);
            server.execute(() -> spec.serverHandler().accept(message, player));
        });
    }

    static <T> void sendToPlayer(MessageSpec<T> spec, ServerPlayer player, T message) {
        ServerPlayNetworking.send(player, spec.id(), encode(spec, message));
    }

    static <T> FriendlyByteBuf encode(MessageSpec<T> spec, T message) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        spec.encoder().accept(message, buf);
        return buf;
    }
}
