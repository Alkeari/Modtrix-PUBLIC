package com.alkeari.modtrix.neoforge;

import java.util.ArrayList;
import java.util.List;
import com.alkeari.modtrix.impl.MessageSpec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;

/** NeoForge 20.4 payloads: each writes itself and names its id, registered when the mod bus asks for them. */
final class NeoForgeNetwork {
    private final List<MessageSpec<?>> specs = new ArrayList<>();

    NeoForgeNetwork(IEventBus modBus) {
        modBus.addListener(this::onRegisterPayloads);
    }

    record Payload<T>(MessageSpec<T> spec, T value) implements CustomPacketPayload {
        @Override
        public void write(FriendlyByteBuf buf) {
            spec.encoder().accept(value, buf);
        }

        @Override
        public ResourceLocation id() {
            return spec.id();
        }
    }

    synchronized void add(MessageSpec<?> spec) {
        specs.add(spec);
    }

    private synchronized void onRegisterPayloads(RegisterPayloadHandlerEvent event) {
        for (MessageSpec<?> spec : specs) {
            register(event.registrar(spec.id().getNamespace()), spec);
        }
    }

    private static <T> void register(IPayloadRegistrar registrar, MessageSpec<T> spec) {
        FriendlyByteBuf.Reader<Payload<T>> reader = buf -> new Payload<>(spec, spec.decoder().apply(buf));
        if (spec.toClient()) {
            registrar.play(spec.id(), reader, handlers -> handlers.client((payload, context) ->
                    context.workHandler().execute(() -> spec.clientHandler().accept(payload.value()))));
        } else {
            registrar.play(spec.id(), reader, handlers -> handlers.server((payload, context) ->
                    context.player().ifPresent(player -> context.workHandler().execute(() ->
                            spec.serverHandler().accept(payload.value(), (ServerPlayer) player)))));
        }
    }

    static <T> void sendToPlayer(MessageSpec<T> spec, ServerPlayer player, T message) {
        PacketDistributor.PLAYER.with(player).send(new Payload<>(spec, message));
    }

    static <T> void sendToServer(MessageSpec<T> spec, T message) {
        PacketDistributor.SERVER.noArg().send(new Payload<>(spec, message));
    }
}
