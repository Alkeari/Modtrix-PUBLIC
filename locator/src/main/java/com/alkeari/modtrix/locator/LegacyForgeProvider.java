package com.alkeari.modtrix.locator;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileDependencyLocator;
import net.minecraftforge.forgespi.locating.IModFile;

/** FML 47 and 48, and the NeoForge 1.20.1 fork. */
final class LegacyForgeProvider extends AbstractJarFileDependencyLocator implements ForgeModProvider {
    @Override
    public List<IModFile> load(Path jar) {
        var result = createMod(jar);
        if (result.ex() != null) {
            throw result.ex();
        }
        return List.of(result.file());
    }

    @Override
    public List<IModFile> scanMods(Iterable<IModFile> loadedMods) {
        return List.of();
    }

    @Override
    public String name() {
        return NestedJar.NAME;
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
    }
}
