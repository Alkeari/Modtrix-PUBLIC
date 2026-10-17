package com.alkeari.modtrix.neoforge;

import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class NeoForgeTicks {
    private NeoForgeTicks() {
    }

    static void onServerTick(Consumer<MinecraftServer> listener) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> listener.accept(event.getServer()));
    }
}
