package net.neoforged.neoforgespi.locating;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/** FML 2 shape. */
public interface IModProvider {
    String name();

    void scanFile(IModFile file, Consumer<Path> pathConsumer);

    void initArguments(Map<String, ?> arguments);

    boolean isValid(IModFile file);
}
