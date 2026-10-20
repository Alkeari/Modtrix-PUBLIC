package com.alkeari.modtrix.neoforge;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.alkeari.modtrix.impl.MessageSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.NetworkEvent;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.PlayNetworkDirection;
import net.neoforged.neoforge.network.simple.SimpleChannel;

/** NeoForge 20.2 and 20.3 networking: one simple channel per namespace, each message registered on it at once. */
final class NeoForgeNetwork {
    private static final String PROTOCOL = "1";
    private static final Map<String, SimpleChannel> CHANNELS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> NEXT_INDEX = new ConcurrentHashMap<>();

    NeoForgeNetwork(IEventBus modBus) {
    }

    void add(MessageSpec<?> spec) {
        register(spec);
    }

    private static <T> void register(MessageSpec<T> spec) {
        String namespace = spec.id().getNamespace();
        int index = NEXT_INDEX.computeIfAbsent(namespace, key -> new AtomicInteger()).getAndIncrement();
        PlayNetworkDirection direction = spec.toClient() ? PlayNetworkDirection.PLAY_TO_CLIENT : PlayNetworkDirection.PLAY_TO_SERVER;
        channel(namespace).registerMessage(index, spec.type(), spec.encoder()::accept, spec.decoder()::apply,
                (T message, NetworkEvent.Context context) -> {
                    if (spec.toClient()) {
                        context.enqueueWork(() -> spec.clientHandler().accept(message));
                    } else {
                        ServerPlayer sender = context.getSender();
                        context.enqueueWork(() -> spec.serverHandler().accept(message, sender));
                    }
                    context.setPacketHandled(true);
                }, Optional.of(direction));
    }

    static <T> void sendToPlayer(MessageSpec<T> spec, ServerPlayer player, T message) {
        channel(spec.id().getNamespace()).send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    static <T> void sendToServer(MessageSpec<T> spec, T message) {
        channel(spec.id().getNamespace()).send(PacketDistributor.SERVER.noArg(), message);
    }

    private static SimpleChannel channel(String namespace) {
        return CHANNELS.computeIfAbsent(namespace, key -> NetworkRegistry.newSimpleChannel(
                Objects.requireNonNull(ResourceLocation.tryBuild(key, "modtrix")), () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL)));
    }
}
