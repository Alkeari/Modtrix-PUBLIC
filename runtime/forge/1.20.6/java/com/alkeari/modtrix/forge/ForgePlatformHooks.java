package com.alkeari.modtrix.forge;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;

public final class ForgePlatformHooks implements PlatformHooks {
    private static final int PROTOCOL = 1;
    private final Map<String, SimpleChannel> channels = new ConcurrentHashMap<>();

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
    public String modVersion(String modId) {
        return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getVersion().toString()).orElse(null);
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

    // FMLJavaModLoadingContext.get() is marked for removal but still present, and saves every mod passing its context in.
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
        // The builder takes the message class as its key, so every message type needs its own class.
        if (spec.toClient()) {
            channel(spec.id().getNamespace()).messageBuilder(spec.type(), NetworkDirection.PLAY_TO_CLIENT)
                    .encoder((T message, net.minecraft.network.RegistryFriendlyByteBuf buf) -> spec.encoder().accept(message, buf))
                    .decoder(buf -> spec.decoder().apply(buf))
                    .consumerMainThread((message, context) -> spec.clientHandler().accept(message))
                    .add();
        } else {
            channel(spec.id().getNamespace()).messageBuilder(spec.type(), NetworkDirection.PLAY_TO_SERVER)
                    .encoder((T message, net.minecraft.network.RegistryFriendlyByteBuf buf) -> spec.encoder().accept(message, buf))
                    .decoder(buf -> spec.decoder().apply(buf))
                    .consumerMainThread((message, context) -> spec.serverHandler().accept(message, context.getSender()))
                    .add();
        }
    }

    @Override
    public <T> void sendToPlayer(MessageSpec<T> spec, ServerPlayer player, T message) {
        channel(spec.id().getNamespace()).send(message, PacketDistributor.PLAYER.with(player));
    }

    SimpleChannel channel(String namespace) {
        return channels.computeIfAbsent(namespace, key -> ChannelBuilder
                .named(Objects.requireNonNull(ResourceLocation.tryBuild(key, "modtrix")))
                .networkProtocolVersion(PROTOCOL)
                .optional()
                .simpleChannel());
    }

    static ForgePlatformHooks instance() {
        return (ForgePlatformHooks) Hooks.platform();
    }
}
