package com.alkeari.modtrix.locator;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraftforge.fml.loading.moddiscovery.AbstractModProvider;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;

/**
 * FML 49 and later. This class compiles against FML 47, where {@code createMod} takes {@code Path...} and
 * {@code AbstractModProvider} has no {@code scanFile}; FML 49 takes a single {@code Path} and implements {@code scanFile}
 * there, so both are looked up at run time.
 */
final class CurrentForgeProvider extends AbstractModProvider implements ForgeModProvider {
    @Override
    public List<IModFile> load(Path jar) {
        IModLocator.ModFileOrException result;
        try {
            Method createMod = AbstractModProvider.class.getDeclaredMethod("createMod", Path.class);
            result = (IModLocator.ModFileOrException) createMod.invoke(this, jar);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FML has no createMod(Path)", e);
        }
        if (result.ex() != null) {
            throw result.ex();
        }
        return List.of(result.file());
    }

    @Override
    public void scanFile(IModFile file, Consumer<Path> pathConsumer) {
        try {
            MethodHandles.lookup()
                    .findSpecial(AbstractModProvider.class, "scanFile", MethodType.methodType(void.class, IModFile.class, Consumer.class), CurrentForgeProvider.class)
                    .invoke(this, file, pathConsumer);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("FML has no AbstractModProvider.scanFile", e);
        }
    }

    @Override
    public String name() {
        return NestedJar.NAME;
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
    }
}
