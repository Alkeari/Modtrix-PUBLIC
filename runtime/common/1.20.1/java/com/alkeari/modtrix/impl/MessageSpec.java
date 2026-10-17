package com.alkeari.modtrix.impl;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** One message type: exactly one of {@code clientHandler} and {@code serverHandler} is set. */
public record MessageSpec<T>(ResourceLocation id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
        Function<FriendlyByteBuf, T> decoder, Consumer<T> clientHandler, BiConsumer<T, ServerPlayer> serverHandler) {
    public boolean toClient() {
        return clientHandler != null;
    }
}
