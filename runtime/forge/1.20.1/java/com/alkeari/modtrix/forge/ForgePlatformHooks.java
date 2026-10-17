package com.alkeari.modtrix.forge;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.alkeari.modtrix.api.CommandRegistration;
import com.alkeari.modtrix.impl.Hooks;
import com.alkeari.modtrix.impl.MessageSpec;
import com.alkeari.modtrix.impl.PlatformHooks;
import com.alkeari.modtrix.impl.RegistrarBackend;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;

public final class ForgePlatformHooks implements PlatformHooks {
    private static final String PROTOCOL = "1";
    private final Map<String, SimpleChannel> channels = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> nextIndex = new ConcurrentHashMap<>();

    @Override
    public String loader() {
        return "forge";
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }

    @Override
    public java.nio.file.Path configDir() {
        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public String modName(String modId) {
        return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getDisplayName()).orElse(null);
    }

    @Override
    public void onRegisterCommands(CommandRegistration registration) {
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                registration.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
    }

    @Override
    public void onServerStopping(Consumer<MinecraftServer> listener) {
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> listener.accept(event.getServer()));
    }

    @Override
    public void onServerTick(Consumer<MinecraftServer> listener) {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                listener.accept(event.getServer());
            }
        });
    }

    @Override
    public void onPlayerJoin(Consumer<ServerPlayer> listener) {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                listener.accept(player);
            }
        });
    }

    // FMLJavaModLoadingContext.get() is marked for removal in later Forge builds but is the one call every Forge 47 build has.
    @Override
    @SuppressWarnings("removal")
    public <T> RegistrarBackend<T> registrar(String modId, ResourceKey<? extends Registry<T>> registry) {
        DeferredRegister<T> deferred = DeferredRegister.create(registry, modId);
        deferred.register(FMLJavaModLoadingContext.get().getModEventBus());
        return new RegistrarBackend<>() {
            @Override
            public <E extends T> Supplier<E> register(String name, Supplier<E> factory) {
                return deferred.register(name, factory);
            }
        };
    }

    @Override
    public <T> void registerMessage(MessageSpec<T> spec) {
        String namespace = spec.id().getNamespace();
        SimpleChannel channel = channel(namespace);
        int index = nextIndex.computeIfAbsent(namespace, key -> new AtomicInteger()).getAndIncrement();
        NetworkDirection direction = spec.toClient() ? NetworkDirection.PLAY_TO_CLIENT : NetworkDirection.PLAY_TO_SERVER;
        channel.registerMessage(index, spec.type(), spec.encoder(), spec.decoder(),
                (T message, Supplier<NetworkEvent.Context> context) -> {
                    NetworkEvent.Context ctx = context.get();
                    if (spec.toClient()) {
                        ctx.enqueueWork(() -> spec.clientHandler().accept(message));
                    } else {
                        ServerPlayer sender = ctx.getSender();
                        ctx.enqueueWork(() -> spec.serverHandler().accept(message, sender));
                    }
                    ctx.setPacketHandled(true);
                }, Optional.of(direction));
    }

    @Override
    public <T> void sendToPlayer(MessageSpec<T> spec, ServerPlayer player, T message) {
        channel(spec.id().getNamespace()).send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    SimpleChannel channel(String namespace) {
        return channels.computeIfAbsent(namespace, key -> NetworkRegistry.newSimpleChannel(
                Objects.requireNonNull(ResourceLocation.tryBuild(key, "modtrix")), () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL)));
    }

    static ForgePlatformHooks instance() {
        return (ForgePlatformHooks) Hooks.platform();
    }
}
