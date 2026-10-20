package com.alkeari.modtrix.neoforge;

import net.neoforged.fml.loading.FMLEnvironment;

/** FML 10 (NeoForge 21.9) replaced the dist field with an accessor. */
final class NeoForgeEnvironment {
    private NeoForgeEnvironment() {
    }

    static boolean isClient() {
        return FMLEnvironment.getDist().isClient();
    }
}
