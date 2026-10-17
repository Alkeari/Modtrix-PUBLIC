package com.alkeari.modtrix.api;

import java.util.function.Consumer;
import com.alkeari.modtrix.impl.Hooks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Game events every loader fires, under one name. Register during mod initialization. */
public final class Events {
    private Events() {
    }

    /** Called whenever the server builds its command tree. */
    public static void onRegisterCommands(CommandRegistration registration) {
        Hooks.platform().onRegisterCommands(registration);
    }

    /** Called when the server starts shutting down, before worlds are saved. */
    public static void onServerStopping(Consumer<MinecraftServer> listener) {
        Hooks.platform().onServerStopping(listener);
    }

    /** Called after every server tick. */
    public static void onServerTick(Consumer<MinecraftServer> listener) {
        Hooks.platform().onServerTick(listener);
    }

    /** Called when a player has joined the server. */
    public static void onPlayerJoin(Consumer<ServerPlayer> listener) {
        Hooks.platform().onPlayerJoin(listener);
    }

    /** Called after every client tick; does nothing on a dedicated server. */
    public static void onClientTick(Runnable listener) {
        if (Modtrix.isClient()) {
            Hooks.client().onClientTick(listener);
        }
    }
}
