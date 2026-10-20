package com.alkeari.modtrix.neoforge;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeClientTicks {
    private NeoForgeClientTicks() {
    }

    static void onClientTick(Runnable listener) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> listener.run());
    }
}
