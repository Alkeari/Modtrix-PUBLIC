package com.alkeari.modtrix.locator;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.loading.moddiscovery.AbstractJarFileModProvider;
import net.neoforged.neoforgespi.locating.IModFile;

/** FML 2 only: builds the mod file through FML's own jar provider, which FML 3 removed. */
final class LegacyNeoForgeProvider extends AbstractJarFileModProvider {
    List<IModFile> load(Path jar) {
        var result = createMod(jar);
        if (result.ex() != null) {
            throw result.ex();
        }
        return List.of(result.file());
    }

    @Override
    public String name() {
        return NestedJar.NAME;
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
    }
}
