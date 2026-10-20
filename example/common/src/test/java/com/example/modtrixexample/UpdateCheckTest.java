package com.example.modtrixexample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.alkeari.modtrix.impl.UpdateCheck;
import com.alkeari.modtrix.impl.UpdateCheck.Notice;
import org.junit.jupiter.api.Test;

class UpdateCheckTest {
    private static final String FILE = """
            {"name": "GeoTectonic", "page": "https://example.com/geotectonic",
             "lines": [
               {"gameVersion": "1.21.1", "loaders": ["fabric", "neoforge"], "version": "1.0.2-1.21.1"},
               {"gameVersion": "1.20.1", "loaders": ["fabric", "forge"], "version": "1.0.3-1.20.1"},
               {"gameVersion": "1.20.1 to 1.21.1", "loaders": [], "version": "2.0.0"}
             ]}
            """;

    @Test
    void newestMatchingLineWins() {
        assertEquals(new Notice("GeoTectonic", "1.0.2", "2.0.0", "https://example.com/geotectonic"),
                UpdateCheck.find(FILE, "1.21.1", "fabric", "1.0.2-1.21.1", "geotectonic"));
    }

    @Test
    void nothingWhenRunningTheNewest() {
        assertNull(UpdateCheck.find(FILE, "1.20.4", "neoforge", "2.0.0", "geotectonic"));
    }

    @Test
    void lineOutsideTheGameVersionOrLoaderIsIgnored() {
        String file = """
                {"page": "https://example.com/m", "lines": [
                  {"gameVersion": "1.20.1 to 1.20.6", "loaders": ["forge"], "version": "3.0.0"},
                  {"gameVersion": "1.21", "loaders": ["fabric"], "version": "4.0.0"}
                ]}
                """;
        assertNull(UpdateCheck.find(file, "1.21.1", "fabric", "1.0.0", "m"));
        assertNull(UpdateCheck.find(file, "1.20.4", "fabric", "1.0.0", "m"));
        assertEquals("3.0.0", UpdateCheck.find(file, "1.20.4", "forge", "1.0.0", "m").latest());
    }

    @Test
    void versionsCompareByNumberNotText() {
        String file = """
                {"page": "https://example.com/m", "lines": [{"gameVersion": "1.21.11 to 26.3", "loaders": [], "version": "1.10.0"}]}
                """;
        assertEquals("1.10.0", UpdateCheck.find(file, "26.1", "forge", "1.9.5", "m").latest());
        assertNull(UpdateCheck.find(file, "1.21.2", "forge", "1.9.5", "m"));
    }

    @Test
    void storeLinkStandsInForAMissingPageAndModNameForAMissingName() {
        String file = """
                {"lines": [{"gameVersion": "1.20.1", "loaders": [], "version": "1.1.0",
                  "stores": [{"store": "modrinth", "url": "https://modrinth.com/mod/m", "version": "1.1.0"}]}]}
                """;
        assertEquals(new Notice("My Mod", "1.0.0", "1.1.0", "https://modrinth.com/mod/m"),
                UpdateCheck.find(file, "1.20.1", "fabric", "1.0.0", "My Mod"));
    }
}
