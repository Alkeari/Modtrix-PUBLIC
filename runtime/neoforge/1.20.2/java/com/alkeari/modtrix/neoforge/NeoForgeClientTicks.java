package com.alkeari.modtrix.neoforge;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;

final class NeoForgeClientTicks {
    private NeoForgeClientTicks() {
    }

    static void onClientTick(Runnable listener) {
        NeoForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                listener.run();
            }
        });
    }
}
