package com.alkeari.modtrix.locator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;

/**
 * Finds the build of the mod nested in this jar for one loader and Minecraft version, and opens it in place.
 * {@code META-INF/<this package>/<loader>.txt} lists one nested jar per line with the Minecraft range it serves.
 * Each mod relocates this package into its own, so the path and the locator name are unique to that mod.
 */
final class NestedJar {
    static final String NAME = NestedJar.class.getPackageName();
    private static final String ROOT = "/META-INF/" + NAME + "/";

    private NestedJar() {
    }

    static Path select(String loader, String minecraftVersion) {
        String jarName = jarFor(loader, minecraftVersion);
        if (jarName == null) {
            return null;
        }
        try {
            // Forge 1.20.3 (FML 49.0.2) reads no manifest from a jar opened through jij:, so Mixin never sees its
            // MixinConfigs and the mod loads without its mixins; every Forge takes the copied jar so all run one path.
            if ("forge".equals(loader)) {
                return extract(jarName);
            }
            URL jar = NestedJar.class.getResource(ROOT + jarName);
            if (jar == null) {
                throw new IllegalStateException("Missing nested jar " + jarName);
            }
            Path inner;
            try {
                inner = Path.of(jar.toURI());
            } catch (FileSystemNotFoundException notMounted) {
                return extract(jarName);
            }
            URI uri = new URI("jij:" + inner.toAbsolutePath().toUri().getRawSchemeSpecificPart()).normalize();
            try {
                return FileSystems.newFileSystem(uri, Map.of("packagePath", inner)).getPath("/");
            } catch (ProviderNotFoundException noJij) {
                return extract(jarName);
            }
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Could not open nested jar " + jarName, e);
        }
    }

    /**
     * Copies the nested jar out once per content, under the system temp folder, for loaders that cannot use it in place:
     * every Forge (see {@link #select}) and FML 10 (NeoForge 21.9), which loads this jar from a plain class path and
     * dropped the {@code jij:} file system.
     */
    private static Path extract(String jarName) throws IOException {
        byte[] bytes;
        try (InputStream in = NestedJar.class.getResourceAsStream(ROOT + jarName)) {
            if (in == null) {
                throw new IllegalStateException("Missing nested jar " + jarName);
            }
            bytes = in.readAllBytes();
        }
        Path target = Path.of(System.getProperty("java.io.tmpdir"), NAME, Integer.toHexString(Arrays.hashCode(bytes)), jarName);
        if (!Files.isRegularFile(target) || Files.size(target) != bytes.length) {
            Files.createDirectories(target.getParent());
            Path partial = Files.createTempFile(target.getParent(), jarName, ".part");
            Files.write(partial, bytes);
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        return target;
    }

    static String jarFor(String loader, String minecraftVersion) {
        try (InputStream index = NestedJar.class.getResourceAsStream(ROOT + loader + ".txt")) {
            if (index == null) {
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length == 2 && inRange(minecraftVersion, fields[1])) {
                    return fields[0];
                }
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the nested jar index for " + loader, e);
        }
    }

    /** Maven range with one interval, such as {@code [1.20.4,1.20.6)} or {@code [1.21,)}, or one exact version, {@code [1.21.1]}. */
    static boolean inRange(String version, String range) {
        String body = range.substring(1, range.length() - 1);
        int comma = body.indexOf(',');
        if (comma < 0) {
            return compare(version, body.trim()) == 0;
        }
        String low = body.substring(0, comma).trim();
        String high = body.substring(comma + 1).trim();
        if (!low.isEmpty()) {
            int c = compare(version, low);
            if (c < 0 || (c == 0 && range.charAt(0) == '(')) {
                return false;
            }
        }
        if (!high.isEmpty()) {
            int c = compare(version, high);
            if (c > 0 || (c == 0 && range.charAt(range.length() - 1) == ')')) {
                return false;
            }
        }
        return true;
    }

    static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int left = i < x.length ? Integer.parseInt(x[i]) : 0;
            int right = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        return 0;
    }
}
