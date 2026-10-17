package com.alkeari.modtrix.api;

import com.alkeari.modtrix.impl.Hooks;
import com.alkeari.modtrix.impl.MessageSpec;
import net.minecraft.server.level.ServerPlayer;

public final class MessageType<T> {
    private final MessageSpec<T> spec;

    MessageType(MessageSpec<T> spec) {
        this.spec = spec;
    }

    /** Sends to one player; only for a type made with {@link Network#toClient}. */
    public void sendTo(ServerPlayer player, T message) {
        if (!spec.toClient()) {
            throw new IllegalStateException(spec.id() + " is a client-to-server message");
        }
        Hooks.platform().sendToPlayer(spec, player, message);
    }

    /** Sends from this client to the server; only for a type made with {@link Network#toServer}. */
    public void sendToServer(T message) {
        if (spec.toClient()) {
            throw new IllegalStateException(spec.id() + " is a server-to-client message");
        }
        Hooks.client().sendToServer(spec, message);
    }
}
