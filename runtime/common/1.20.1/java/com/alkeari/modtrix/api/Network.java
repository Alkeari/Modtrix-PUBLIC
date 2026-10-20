package com.alkeari.modtrix.api;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import com.alkeari.modtrix.impl.Hooks;
import com.alkeari.modtrix.impl.MessageSpec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Messages between server and client. Each message type has its own Java class, an encoder and a decoder; its handler
 * runs on the game thread. Register every type during mod initialization, on both sides.
 */
public final class Network {
    private Network() {
    }

    /** A message the server sends to a player; {@code handler} runs in that player's client. */
    public static <T> MessageType<T> toClient(ResourceLocation id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder, Consumer<T> handler) {
        MessageSpec<T> spec = new MessageSpec<>(id, type, encoder, decoder, handler, null);
        Hooks.platform().registerMessage(spec);
        return new MessageType<>(spec);
    }

    /** A message a client sends to the server; {@code handler} runs on the server with the player who sent it. */
    public static <T> MessageType<T> toServer(ResourceLocation id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder, BiConsumer<T, ServerPlayer> handler) {
        MessageSpec<T> spec = new MessageSpec<>(id, type, encoder, decoder, null, handler);
        Hooks.platform().registerMessage(spec);
        return new MessageType<>(spec);
    }
}
