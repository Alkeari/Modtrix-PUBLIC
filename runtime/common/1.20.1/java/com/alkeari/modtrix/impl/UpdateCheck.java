package com.alkeari.modtrix.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a mod's update file once per start, off the game thread, and keeps a notice only when it names a newer version
 * for the running Minecraft version and loader. The request carries nothing but the file's address.
 */
public final class UpdateCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger("Modtrix");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static volatile Notice pending;

    public record Notice(String name, String current, String latest, String page) {
    }

    private UpdateCheck() {
    }

    public static void start(String modId, String url) {
        Thread thread = new Thread(() -> run(modId, url), "Modtrix update check (" + modId + ")");
        thread.setDaemon(true);
        thread.start();
    }

    /** The notice not yet shown, cleared as it is taken so a player sees it once per start. */
    public static Notice take() {
        Notice notice = pending;
        pending = null;
        return notice;
    }

    private static void run(String modId, String url) {
        try {
            PlatformHooks platform = Hooks.platform();
            String current = platform.modVersion(modId);
            String minecraft = platform.modVersion("minecraft");
            if (current == null || minecraft == null) {
                return;
            }
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOGGER.debug("{}: update check got HTTP {} from {}", modId, response.statusCode(), url);
                return;
            }
            String fallbackName = platform.modName(modId);
            Notice notice = find(response.body(), minecraft, platform.loader(), current, fallbackName == null ? modId : fallbackName);
            if (notice != null) {
                LOGGER.info("{} {} is available for Minecraft {} on {}; this is {}: {}",
                        notice.name(), notice.latest(), minecraft, platform.loader(), notice.current(), notice.page());
                pending = notice;
            }
        } catch (Exception e) {
            LOGGER.debug("{}: update check of {} failed: {}", modId, url, e.toString());
        }
    }

    /**
     * The newest release in {@code json} that serves {@code minecraft} on {@code loader} and is newer than {@code current},
     * or null. Each entry of {@code lines} names its Minecraft versions as {@code "1.20.1"} or {@code "1.20.1 to 1.21.1"}
     * and its loaders, an empty list meaning every loader.
     */
    public static Notice find(String json, String minecraft, String loader, String current, String fallbackName) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String best = null;
        String page = text(root, "page");
        for (JsonElement element : array(root, "lines")) {
            JsonObject line = element.getAsJsonObject();
            String version = text(line, "version");
            String games = text(line, "gameVersion");
            if (version == null || games == null || !servesGame(games, minecraft) || !servesLoader(array(line, "loaders"), loader)) {
                continue;
            }
            if (compare(base(version), base(current)) > 0 && (best == null || compare(base(version), base(best)) > 0)) {
                best = version;
                if (page == null) {
                    JsonArray stores = array(line, "stores");
                    page = stores.isEmpty() ? null : text(stores.get(0).getAsJsonObject(), "url");
                }
            }
        }
        if (best == null || page == null) {
            return null;
        }
        String name = text(root, "name");
        return new Notice(name == null ? fallbackName : name, base(current), base(best), page);
    }

    private static boolean servesGame(String games, String minecraft) {
        String[] bounds = games.split("\\s+to\\s+");
        return bounds.length == 2
                ? compare(minecraft, bounds[0].trim()) >= 0 && compare(minecraft, bounds[1].trim()) <= 0
                : compare(minecraft, games.trim()) == 0;
    }

    private static boolean servesLoader(JsonArray loaders, String loader) {
        if (loaders.isEmpty()) {
            return true;
        }
        for (JsonElement element : loaders) {
            if (element.getAsString().equalsIgnoreCase(loader)) {
                return true;
            }
        }
        return false;
    }

    /** The release number alone: {@code 1.0.2-1.21.1} and {@code 1.0.2+fabric} are both {@code 1.0.2}. */
    static String base(String version) {
        return version.split("[-+]", 2)[0].trim();
    }

    /** Compares dotted numbers part by part, a missing part counting as zero, so 26.1 is newer than 1.21.11. */
    static int compare(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int difference = Long.compare(number(left, i), number(right, i));
            if (difference != 0) {
                return difference;
            }
        }
        return 0;
    }

    private static long number(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        String digits = parts[index].replaceAll("^(\\d*).*$", "$1");
        return digits.isEmpty() ? 0 : Long.parseLong(digits);
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
}
