package com.alkeari.modtrix.neoforge;

import java.util.function.Consumer;
import java.util.function.Supplier;
import com.alkeari.modtrix.api.CommandRegistration;
import com.alkeari.modtrix.impl.MessageSpec;
import com.alkeari.modtrix.impl.PlatformHooks;
import com.alkeari.modtrix.impl.RegistrarBackend;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NeoForgePlatformHooks implements PlatformHooks {
    private NeoForgeNetwork network;

    @Override
    public String loader() {
        return "neoforge";
    }

    @Override
    public boolean isClient() {
        return NeoForgeEnvironment.isClient();
    }

    @Override
    public java.nio.file.Path configDir() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
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
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                registration.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
    }

    @Override
    public void onServerStopping(Consumer<MinecraftServer> listener) {
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> listener.accept(event.getServer()));
    }

    @Override
    public void onServerTick(Consumer<MinecraftServer> listener) {
        NeoForgeTicks.onServerTick(listener);
    }

    @Override
    public void onPlayerJoin(Consumer<ServerPlayer> listener) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                listener.accept(player);
            }
        });
    }

    @Override
    public <T> RegistrarBackend<T> registrar(String modId, ResourceKey<? extends Registry<T>> registry) {
        DeferredRegister<T> deferred = DeferredRegister.create(registry, modId);
        deferred.register(modBus());
        return new RegistrarBackend<>() {
            @Override
            public <E extends T> Supplier<E> register(String name, Supplier<E> factory) {
                return deferred.register(name, factory);
            }
        };
    }

    @Override
    public synchronized <T> void registerMessage(MessageSpec<T> spec) {
        if (network == null) {
            network = new NeoForgeNetwork(modBus());
        }
        network.add(spec);
    }

    @Override
    public <T> void sendToPlayer(MessageSpec<T> spec, ServerPlayer player, T message) {
        NeoForgeNetwork.sendToPlayer(spec, player, message);
    }

    /** Valid only while the mod that bundles this copy of Modtrix is being constructed. */
    private static IEventBus modBus() {
        return ModLoadingContext.get().getActiveContainer().getEventBus();
    }
}
