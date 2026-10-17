package com.alkeari.modtrix.neoforge;

import net.neoforged.fml.loading.FMLEnvironment;

final class NeoForgeEnvironment {
    private NeoForgeEnvironment() {
    }

    static boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }
}
