package com.alkeari.modtrix.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.alkeari.modtrix.impl.MessageSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** NeoForge from 21.7, where sending to the server moved to the client distributor: each message travels as a payload type, handlers called on the game thread. */
final class NeoForgeNetwork {
    private static final String PROTOCOL = "1";
    private static final Map<ResourceLocation, CustomPacketPayload.Type<Payload<?>>> TYPES = new ConcurrentHashMap<>();
    private final List<MessageSpec<?>> specs = new ArrayList<>();

    NeoForgeNetwork(IEventBus modBus) {
        modBus.addListener(this::onRegisterPayloads);
    }

    record Payload<T>(CustomPacketPayload.Type<Payload<?>> type, T value) implements CustomPacketPayload {
    }

    synchronized void add(MessageSpec<?> spec) {
        TYPES.put(spec.id(), new CustomPacketPayload.Type<>(spec.id()));
        specs.add(spec);
    }

    private synchronized void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        for (MessageSpec<?> spec : specs) {
            register(registrar, spec);
        }
    }

    private static <T> void register(PayloadRegistrar registrar, MessageSpec<T> spec) {
        CustomPacketPayload.Type<Payload<?>> type = TYPES.get(spec.id());
        StreamCodec<RegistryFriendlyByteBuf, Payload<?>> codec = StreamCodec.of(
                (buf, payload) -> spec.encoder().accept(spec.type().cast(payload.value()), buf),
                buf -> new Payload<>(type, spec.decoder().apply(buf)));
        if (spec.toClient()) {
            registrar.playToClient(type, codec, (payload, context) -> spec.clientHandler().accept(spec.type().cast(payload.value())));
        } else {
            registrar.playToServer(type, codec,
                    (payload, context) -> spec.serverHandler().accept(spec.type().cast(payload.value()), (ServerPlayer) context.player()));
        }
    }

    static <T> void sendToPlayer(MessageSpec<T> spec, ServerPlayer player, T message) {
        PacketDistributor.sendToPlayer(player, new Payload<>(TYPES.get(spec.id()), message));
    }

    static <T> void sendToServer(MessageSpec<T> spec, T message) {
        ClientPacketDistributor.sendToServer(new Payload<>(TYPES.get(spec.id()), message));
    }
}
