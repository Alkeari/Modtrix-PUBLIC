package com.alkeari.modtrix.impl;

import java.util.ServiceLoader;

/** Finds this loader's implementations, which ship beside this class in the same mod jar. */
public final class Hooks {
    private static PlatformHooks platform;
    private static ClientHooks client;

    private Hooks() {
    }

    public static synchronized PlatformHooks platform() {
        if (platform == null) {
            platform = load(PlatformHooks.class);
        }
        return platform;
    }

    public static synchronized ClientHooks client() {
        if (client == null) {
            client = load(ClientHooks.class);
        }
        return client;
    }

    private static <S> S load(Class<S> service) {
        return ServiceLoader.load(service, Hooks.class.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + service.getName() + " in the jar that holds " + Hooks.class.getName()));
    }
}
