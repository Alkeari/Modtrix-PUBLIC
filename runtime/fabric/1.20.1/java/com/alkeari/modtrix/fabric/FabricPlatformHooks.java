package com.alkeari.modtrix.fabric;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.alkeari.modtrix.api.CommandRegistration;
import com.alkeari.modtrix.impl.Hooks;
import com.alkeari.modtrix.impl.MessageSpec;
import com.alkeari.modtrix.impl.PlatformHooks;
import com.alkeari.modtrix.impl.RegistrarBackend;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class FabricPlatformHooks implements PlatformHooks {
    @Override
    public String loader() {
        return "fabric";
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public java.nio.file.Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public String modName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(c -> c.getMetadata().getName()).orElse(null);
    }

    @Override
    public void onRegisterCommands(CommandRegistration registration) {
        CommandRegistrationCallback.EVENT.register(registration::register);
    }

    @Override
    public void onServerStopping(Consumer<MinecraftServer> listener) {
        ServerLifecycleEvents.SERVER_STOPPING.register(listener::accept);
    }

    @Override
    public void onServerTick(Consumer<MinecraftServer> listener) {
        ServerTickEvents.END_SERVER_TICK.register(listener::accept);
    }

    @Override
    public void onPlayerJoin(Consumer<ServerPlayer> listener) {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> listener.accept(handler.player));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> RegistrarBackend<T> registrar(String modId, ResourceKey<? extends Registry<T>> registry) {
        Registry<T> target = (Registry<T>) BuiltInRegistries.REGISTRY.getOptional(registry.location())
                .orElseThrow(() -> new IllegalArgumentException("No built-in registry " + registry.location()));
        return new RegistrarBackend<>() {
            @Override
            public <E extends T> Supplier<E> register(String name, Supplier<E> factory) {
                E entry = Registry.register(target, Objects.requireNonNull(ResourceLocation.tryBuild(modId, name)), factory.get());
                return () -> entry;
            }
        };
    }

    @Override
    public <T> void registerMessage(MessageSpec<T> spec) {
        FabricNetwork.register(spec);
        if (spec.toClient() && isClient()) {
            Hooks.client().registerReceiver(spec);
        }
    }

    @Override
    public <T> void sendToPlayer(MessageSpec<T> spec, ServerPlayer player, T message) {
        FabricNetwork.sendToPlayer(spec, player, message);
    }
}
