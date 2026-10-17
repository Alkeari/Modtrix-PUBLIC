package com.alkeari.modtrix.fabric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.alkeari.modtrix.impl.MessageSpec;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Fabric networking from 1.20.5: each message travels as a payload type, handlers called on the game thread. */
final class FabricNetwork {
    private static final Map<ResourceLocation, CustomPacketPayload.Type<Payload<?>>> TYPES = new ConcurrentHashMap<>();

    private FabricNetwork() {
    }

    record Payload<T>(CustomPacketPayload.Type<Payload<?>> type, T value) implements CustomPacketPayload {
    }

    static <T> void register(MessageSpec<T> spec) {
        CustomPacketPayload.Type<Payload<?>> type = new CustomPacketPayload.Type<>(spec.id());
        TYPES.put(spec.id(), type);
        StreamCodec<RegistryFriendlyByteBuf, Payload<?>> codec = StreamCodec.of(
                (buf, payload) -> spec.encoder().accept(spec.type().cast(payload.value()), buf),
                buf -> new Payload<>(type, spec.decoder().apply(buf)));
        if (spec.toClient()) {
            PayloadTypeRegistry.playS2C().register(type, codec);
        } else {
            PayloadTypeRegistry.playC2S().register(type, codec);
            ServerPlayNetworking.registerGlobalReceiver(type,
                    (payload, context) -> spec.serverHandler().accept(spec.type().cast(payload.value()), context.player()));
        }
    }

    static CustomPacketPayload.Type<Payload<?>> type(MessageSpec<?> spec) {
        return TYPES.get(spec.id());
    }

    static <T> Payload<T> wrap(MessageSpec<T> spec, T message) {
        return new Payload<>(type(spec), message);
    }

    static <T> void sendToPlayer(MessageSpec<T> spec, ServerPlayer player, T message) {
        ServerPlayNetworking.send(player, wrap(spec, message));
    }
}
