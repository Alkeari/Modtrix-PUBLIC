package com.alkeari.modtrix.neoforge;

import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;

final class NeoForgeTicks {
    private NeoForgeTicks() {
    }

    static void onServerTick(Consumer<MinecraftServer> listener) {
        NeoForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                listener.accept(event.getServer());
            }
        });
    }
}
