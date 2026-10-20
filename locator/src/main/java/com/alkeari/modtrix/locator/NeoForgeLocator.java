package com.alkeari.modtrix.locator;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * Adds the nested build for the running Minecraft version on every NeoForge. FML 2 (NeoForge 20.4) calls
 * {@link #scanMods(Iterable)}; FML 3 and later call {@link #scanMods(List, IDiscoveryPipeline)}. Each path touches only
 * the types its own FML has.
 */
public final class NeoForgeLocator implements IDependencyLocator {
    private LegacyNeoForgeProvider legacy;

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        Path jar = select();
        if (jar != null) {
            pipeline.addPath(jar, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
        }
    }

    @Override
    public List<IModFile> scanMods(Iterable<IModFile> loadedMods) {
        Path jar = select();
        return jar == null ? List.of() : legacy().load(jar);
    }

    @Override
    public String name() {
        return NestedJar.NAME;
    }

    @Override
    public void scanFile(IModFile file, Consumer<Path> pathConsumer) {
        legacy().scanFile(file, pathConsumer);
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
    }

    @Override
    public boolean isValid(IModFile file) {
        return true;
    }

    private static Path select() {
        return NestedJar.select("neoforge", minecraftVersion());
    }

    /** FML 10 (NeoForge 21.9) removed the static accessor in favor of the current loader's. */
    private static String minecraftVersion() {
        try {
            return FMLLoader.versionInfo().mcVersion();
        } catch (NoSuchMethodError removed) {
            return FMLLoader.getCurrent().getVersionInfo().mcVersion();
        }
    }

    private LegacyNeoForgeProvider legacy() {
        if (legacy == null) {
            legacy = new LegacyNeoForgeProvider();
        }
        return legacy;
    }
}
