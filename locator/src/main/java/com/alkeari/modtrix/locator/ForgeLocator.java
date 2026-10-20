package com.alkeari.modtrix.locator;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.forgespi.locating.IDependencyLocator;
import net.minecraftforge.forgespi.locating.IModFile;

/**
 * Adds the nested build for the running Minecraft version on every Forge, and on the NeoForge 1.20.1 fork that kept
 * Forge's API. FML 47 and 48 build the mod file through {@code AbstractJarFileDependencyLocator}, which FML 49 removed;
 * each provider touches only the types its own FML has.
 */
public final class ForgeLocator implements IDependencyLocator {
    private ForgeModProvider provider;

    @Override
    public List<IModFile> scanMods(Iterable<IModFile> loadedMods) {
        Path jar = NestedJar.select("forge", FMLLoader.versionInfo().mcVersion());
        return jar == null ? List.of() : provider().load(jar);
    }

    @Override
    public String name() {
        return NestedJar.NAME;
    }

    @Override
    public void scanFile(IModFile file, Consumer<Path> pathConsumer) {
        provider().scanFile(file, pathConsumer);
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
    }

    @Override
    public boolean isValid(IModFile file) {
        return true;
    }

    private ForgeModProvider provider() {
        if (provider == null) {
            provider = hasJarFileDependencyLocator() ? new LegacyForgeProvider() : new CurrentForgeProvider();
        }
        return provider;
    }

    private static boolean hasJarFileDependencyLocator() {
        try {
            Class.forName("net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileDependencyLocator", false, ForgeLocator.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException removed) {
            return false;
        }
    }
}
