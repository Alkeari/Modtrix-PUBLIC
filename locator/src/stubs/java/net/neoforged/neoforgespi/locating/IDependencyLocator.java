package net.neoforged.neoforgespi.locating;

import java.util.List;

/** Both shapes: FML 2 extends IModProvider with scanMods(Iterable); FML 3 and later declare scanMods(List, IDiscoveryPipeline). */
public interface IDependencyLocator extends IModProvider {
    List<IModFile> scanMods(Iterable<IModFile> loadedMods);

    void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline);
}
