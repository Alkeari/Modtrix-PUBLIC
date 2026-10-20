package com.alkeari.modtrix.locator;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.minecraftforge.forgespi.locating.IModFile;

/** Builds the mod file for a nested jar through whichever base class the running FML provides. */
interface ForgeModProvider {
    List<IModFile> load(Path jar);

    void scanFile(IModFile file, Consumer<Path> pathConsumer);
}
