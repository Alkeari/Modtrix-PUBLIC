package net.neoforged.neoforgespi.locating;

public interface IModLocator {
    record ModFileOrException(IModFile file, ModFileLoadingException ex) {
    }
}
