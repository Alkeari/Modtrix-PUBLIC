package net.neoforged.fml.loading.moddiscovery;

import java.nio.file.Path;
import java.util.function.Consumer;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModLocator;
import net.neoforged.neoforgespi.locating.IModProvider;

/** FML 2 shape; there createMod is declared on the superclass, which method resolution reaches. */
public abstract class AbstractJarFileModProvider implements IModProvider {
    protected IModLocator.ModFileOrException createMod(Path... path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void scanFile(IModFile file, Consumer<Path> pathConsumer) {
    }

    @Override
    public boolean isValid(IModFile file) {
        return true;
    }
}
