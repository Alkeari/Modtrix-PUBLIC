package net.neoforged.fml.loading;

/** Both shapes: FML 9 and earlier expose the version statically; FML 10 moved it to the current loader instance. */
public class FMLLoader {
    public static VersionInfo versionInfo() {
        throw new UnsupportedOperationException();
    }

    public static FMLLoader getCurrent() {
        throw new UnsupportedOperationException();
    }

    public VersionInfo getVersionInfo() {
        throw new UnsupportedOperationException();
    }
}
