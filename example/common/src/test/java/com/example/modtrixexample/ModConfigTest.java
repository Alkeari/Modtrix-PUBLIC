package com.example.modtrixexample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.alkeari.modtrix.api.ModConfig;
import com.alkeari.modtrix.api.ModConfig.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModConfigTest {
    enum Mode { GENTLE, HARSH }

    @TempDir
    Path dir;

    private record Built(ModConfig config, Value<Integer> size, Value<Double> ratio, Value<Boolean> enabled, Value<String> name, Value<Mode> mode) {
    }

    private Built build() {
        ModConfig.Builder b = ModConfig.builder("testmod");
        Value<Boolean> enabled = b.comment("Turns the feature on.").define("enabled", true);
        b.push("world");
        Value<Integer> size = b.comment("Blocks from the center to the edge.").defineInt("size", 1000, 100, 100000);
        b.push("inner");
        Value<Double> ratio = b.defineDouble("ratio", 0.5, 0.0, 1.0);
        Value<String> name = b.define("name", "Overworld \"main\"");
        b.pop();
        Value<Mode> mode = b.defineEnum("mode", Mode.GENTLE);
        b.pop();
        return new Built(b.build(dir), size, ratio, enabled, name, mode);
    }

    private String file() throws IOException {
        return Files.readString(dir.resolve("testmod.toml"));
    }

    @Test
    void missingFileIsWrittenWithDefaultsCommentsAndRanges() throws IOException {
        Built c = build();
        assertEquals(1000, c.size().get());
        String text = file();
        assertTrue(text.contains("# Turns the feature on.\n# Default: true\nenabled = true"), text);
        assertTrue(text.contains("[world]\n# Blocks from the center to the edge.\n# Range: 100 to 100000. Default: 1000\nsize = 1000"), text);
        assertTrue(text.contains("[world.inner]"), text);
        assertTrue(text.contains("name = \"Overworld \\\"main\\\"\""), text);
        assertTrue(text.contains("# Allowed: GENTLE, HARSH. Default: \"GENTLE\"\nmode = \"GENTLE\""), text);
    }

    @Test
    void editedValuesAreReadBackAfterARestart() throws IOException {
        build();
        Files.writeString(dir.resolve("testmod.toml"), file()
                .replace("size = 1000", "size = 2_500")
                .replace("ratio = 0.5", "ratio = 0.25 # inline comment")
                .replace("enabled = true", "enabled = false")
                .replace("mode = \"GENTLE\"", "mode = \"harsh\"")
                .replace("name = \"Overworld \\\"main\\\"\"", "name = \"Nether # not a comment\""));
        Built c = build();
        assertEquals(2500, c.size().get());
        assertEquals(0.25, c.ratio().get());
        assertFalse(c.enabled().get());
        assertEquals(Mode.HARSH, c.mode().get());
        assertEquals("Nether # not a comment", c.name().get());
    }

    @Test
    void outOfRangeAndUnreadableValuesFallBackToDefaultsAndTheFileIsRepaired() throws IOException {
        build();
        Files.writeString(dir.resolve("testmod.toml"), file()
                .replace("size = 1000", "size = 5")
                .replace("ratio = 0.5", "ratio = banana")
                .replace("mode = \"GENTLE\"", "mode = \"SIDEWAYS\""));
        Built c = build();
        assertEquals(1000, c.size().get());
        assertEquals(0.5, c.ratio().get());
        assertEquals(Mode.GENTLE, c.mode().get());
        assertTrue(file().contains("size = 1000"));
        assertTrue(file().contains("ratio = 0.5"));
    }

    @Test
    void savedValuesSurviveAReloadAndNotifyListeners() throws IOException {
        Built c = build();
        int[] calls = {0};
        c.config().onChange(() -> calls[0]++);
        c.size().set(4000);
        c.config().save();
        assertEquals(1, calls[0]);
        assertEquals(4000, build().size().get());
    }

    @Test
    void setClampsAndScreenTextIsValidated() {
        Built c = build();
        c.size().set(1_000_000);
        assertEquals(100000, c.size().get());
        assertFalse(c.size().setFromText("12.5"));
        assertFalse(c.size().setFromText("99"));
        assertTrue(c.size().setFromText("150"));
        assertEquals(150, c.size().get());
        assertTrue(c.ratio().setFromText("1"));
        assertEquals(1.0, c.ratio().get());
        assertTrue(c.mode().setFromText("harsh"));
        assertEquals(Mode.HARSH, c.mode().get());
        assertTrue(c.name().setFromText("any text"));
        assertFalse(c.enabled().setFromText("maybe"));
    }

    @Test
    void mistakesInTheDeclarationFailFast() {
        ModConfig.Builder dup = ModConfig.builder("dup");
        dup.define("a", true);
        assertThrows(IllegalArgumentException.class, () -> dup.define("a", false));
        assertThrows(IllegalArgumentException.class, () -> ModConfig.builder("x").defineInt("v", 0, 1, 10));
        ModConfig.Builder open = ModConfig.builder("open").push("t");
        assertThrows(IllegalStateException.class, () -> open.build(dir));
    }
}
