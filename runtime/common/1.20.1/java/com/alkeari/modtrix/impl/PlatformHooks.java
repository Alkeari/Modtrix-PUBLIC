package com.alkeari.modtrix.impl;

import java.nio.file.Path;
import java.util.function.Consumer;
import com.alkeari.modtrix.api.CommandRegistration;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** What each loader implements for both sides; found through {@link java.util.ServiceLoader}. */
public interface PlatformHooks {
    String loader();

    boolean isClient();

    boolean isModLoaded(String modId);

    /** The display name the mod's metadata gives, or null when no loaded mod has that id. */
    String modName(String modId);

    /** The version the mod's metadata gives, or null; {@code minecraft} gives the running Minecraft version. */
    String modVersion(String modId);

    /** The loader's config folder, where {@code <modid>.toml} lives. */
    Path configDir();

    void onRegisterCommands(CommandRegistration registration);

    void onServerStopping(Consumer<MinecraftServer> listener);

    void onServerTick(Consumer<MinecraftServer> listener);

    void onPlayerJoin(Consumer<ServerPlayer> listener);

    <T> RegistrarBackend<T> registrar(String modId, ResourceKey<? extends Registry<T>> registry);

    /** Registers the type on both sides; a client receiver is added only in the game client. */
    <T> void registerMessage(MessageSpec<T> spec);

    <T> void sendToPlayer(MessageSpec<T> spec, ServerPlayer player, T message);
}
