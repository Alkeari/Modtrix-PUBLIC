package com.alkeari.modtrix.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import com.alkeari.modtrix.impl.Hooks;
import com.alkeari.modtrix.impl.config.Toml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A mod's settings, declared once and kept in {@code config/<modid>.toml} on every loader. In the game client the same
 * settings get a screen, opened from Mod Menu on Fabric and from the Config button on Forge and NeoForge.
 *
 * <pre>{@code
 * ModConfig.Builder b = ModConfig.builder("mymod");
 * b.push("world");
 * Value<Integer> size = b.comment("Blocks from the center to the edge.").defineInt("size", 1000, 100, 100000);
 * b.pop();
 * ModConfig config = b.build();
 * }</pre>
 *
 * Labels and tooltips on the screen come from the lang keys {@code <modid>.config.<path>} and
 * {@code <modid>.config.<path>.tooltip} when the mod provides them, and from the key and its comment otherwise.
 */
public final class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Modtrix");
    private static final Map<String, ModConfig> BY_MOD = new LinkedHashMap<>();

    private final String modId;
    private final Path file;
    private final List<Value<?>> values;
    private final List<Runnable> listeners = new ArrayList<>();

    private ModConfig(String modId, Path file, List<Value<?>> values) {
        this.modId = modId;
        this.file = file;
        this.values = Collections.unmodifiableList(values);
    }

    public static Builder builder(String modId) {
        return new Builder(modId);
    }

    /** The config a mod built, or null; the settings screen finds its config this way. */
    public static ModConfig of(String modId) {
        synchronized (BY_MOD) {
            return BY_MOD.get(modId);
        }
    }

    /** This mod's config: the runtime is relocated into each mod, so its registry holds that mod's configs alone. */
    public static ModConfig only() {
        synchronized (BY_MOD) {
            return BY_MOD.isEmpty() ? null : BY_MOD.values().iterator().next();
        }
    }

    public String modId() {
        return modId;
    }

    public Path file() {
        return file;
    }

    /** Every setting in declaration order. */
    public List<Value<?>> values() {
        return values;
    }

    /** Runs after every load and every save, so a mod can copy the values where it uses them. */
    public void onChange(Runnable listener) {
        listeners.add(listener);
    }

    /** Reads the file again; a missing, unreadable or out-of-range value falls back to its default and the file is rewritten. */
    public void load() {
        String text = "";
        if (Files.isRegularFile(file)) {
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.warn("{}: cannot read {}, using defaults: {}", modId, file, e.toString());
            }
        }
        List<String> problems = new ArrayList<>();
        Map<String, Object> read = Toml.read(text, problems);
        for (Value<?> value : values) {
            Object raw = read.get(value.path());
            if (raw == null) {
                value.reset();
            } else if (!value.accept(raw)) {
                problems.add(value.path() + " = " + raw + " is not " + value.describe() + "; using " + value.formatDefault());
                value.reset();
            }
        }
        problems.forEach(p -> LOGGER.warn("{}: {}: {}", modId, file.getFileName(), p));
        write();
        listeners.forEach(Runnable::run);
    }

    /** Writes the current values, with each one's comment, range and default above it. */
    public void save() {
        write();
        listeners.forEach(Runnable::run);
    }

    private void write() {
        Map<String, Toml.Table> tables = new LinkedHashMap<>();
        for (Value<?> value : values) {
            Toml.Table table = tables.computeIfAbsent(value.table(), Toml.Table::new);
            List<String> comments = new ArrayList<>(value.comments());
            comments.add(value.rangeLine());
            table.entries().add(new Toml.Entry(value.key(), comments, value.formatCurrent()));
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Toml.write(new ArrayList<>(tables.values())), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("{}: cannot write {}: {}", modId, file, e.toString());
        }
    }

    /** One setting. {@link #set} clamps a number to its range, so a value read from it is always valid. */
    public static final class Value<T> {
        private final String table;
        private final String key;
        private final List<String> comments;
        private final T defaultValue;
        private final T min;
        private final T max;
        private final Class<T> type;
        private final Function<Object, T> convert;
        private volatile T current;

        private Value(String table, String key, List<String> comments, T defaultValue, T min, T max, Class<T> type, Function<Object, T> convert) {
            this.table = table;
            this.key = key;
            this.comments = List.copyOf(comments);
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
            this.type = type;
            this.convert = convert;
            this.current = defaultValue;
        }

        public T get() {
            return current;
        }

        public void set(T value) {
            current = clamp(Objects.requireNonNull(value));
        }

        public void reset() {
            current = defaultValue;
        }

        public T defaultValue() {
            return defaultValue;
        }

        /** Null when the setting has no range. */
        public T min() {
            return min;
        }

        public T max() {
            return max;
        }

        public Class<T> type() {
            return type;
        }

        public String key() {
            return key;
        }

        /** Dotted path from the top of the file, such as {@code world.size}. */
        public String path() {
            return table.isEmpty() ? key : table + "." + key;
        }

        String table() {
            return table;
        }

        public List<String> comments() {
            return comments;
        }

        /** True when {@code text} is a valid value for this setting, as typed on the settings screen. */
        public boolean accepts(String text) {
            Object raw = type == String.class ? text : Toml.parseValue(text.trim());
            if (raw == null && type.isEnum()) {
                raw = text.trim();
            }
            T parsed = raw == null ? null : convert.apply(raw);
            return parsed != null && inRange(parsed);
        }

        /** Sets the value from screen text, returning false and changing nothing when it is not valid. */
        public boolean setFromText(String text) {
            if (!accepts(text)) {
                return false;
            }
            Object raw = type == String.class ? text : Toml.parseValue(text.trim());
            set(convert.apply(raw == null ? text.trim() : raw));
            return true;
        }

        boolean accept(Object raw) {
            T parsed = convert.apply(raw);
            if (parsed == null || !inRange(parsed)) {
                return false;
            }
            current = parsed;
            return true;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private boolean inRange(T value) {
            if (min == null) {
                return true;
            }
            Comparable c = (Comparable) value;
            return c.compareTo(min) >= 0 && c.compareTo(max) <= 0;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private T clamp(T value) {
            if (min == null) {
                return value;
            }
            Comparable c = (Comparable) value;
            if (c.compareTo(min) < 0) {
                return min;
            }
            return c.compareTo(max) > 0 ? max : value;
        }

        public String formatCurrent() {
            return format(current);
        }

        String formatDefault() {
            return format(defaultValue);
        }

        String format(T value) {
            if (value instanceof String s) {
                return Toml.quote(s);
            }
            if (value instanceof Enum<?> e) {
                return Toml.quote(e.name());
            }
            return String.valueOf(value);
        }

        String describe() {
            if (type.isEnum()) {
                return "one of " + String.join(", ", enumNames());
            }
            if (min != null) {
                return "a " + simpleType() + " from " + min + " to " + max;
            }
            return "a " + simpleType();
        }

        private String rangeLine() {
            String allowed = type.isEnum() ? "Allowed: " + String.join(", ", enumNames()) + ". "
                    : min != null ? "Range: " + min + " to " + max + ". " : "";
            return allowed + "Default: " + format(defaultValue);
        }

        public List<String> enumNames() {
            List<String> names = new ArrayList<>();
            for (T constant : type.getEnumConstants()) {
                names.add(((Enum<?>) constant).name());
            }
            return names;
        }

        private String simpleType() {
            return type == Integer.class ? "whole number" : type == Double.class ? "number" : type == Boolean.class ? "true or false" : "text";
        }
    }

    public static final class Builder {
        private final String modId;
        private final List<String> tables = new ArrayList<>();
        private final List<Value<?>> values = new ArrayList<>();
        private List<String> pendingComments = new ArrayList<>();

        private Builder(String modId) {
            this.modId = modId;
        }

        /** Opens a table; settings defined until the matching {@link #pop} go under it. */
        public Builder push(String table) {
            tables.add(table);
            return this;
        }

        public Builder pop() {
            if (tables.isEmpty()) {
                throw new IllegalStateException("pop() without push()");
            }
            tables.remove(tables.size() - 1);
            return this;
        }

        /** Comment lines written above the next setting in the file. */
        public Builder comment(String... lines) {
            pendingComments = new ArrayList<>(List.of(lines));
            return this;
        }

        public Value<Boolean> define(String key, boolean defaultValue) {
            return add(key, defaultValue, null, null, Boolean.class, raw -> raw instanceof Boolean b ? b : null);
        }

        public Value<String> define(String key, String defaultValue) {
            return add(key, defaultValue, null, null, String.class, raw -> raw instanceof String s ? s : null);
        }

        public Value<Integer> defineInt(String key, int defaultValue, int min, int max) {
            return add(key, defaultValue, min, max, Integer.class,
                    raw -> raw instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (int) (long) l : null);
        }

        public Value<Double> defineDouble(String key, double defaultValue, double min, double max) {
            return add(key, defaultValue, min, max, Double.class, raw -> raw instanceof Number n ? n.doubleValue() : null);
        }

        public <E extends Enum<E>> Value<E> defineEnum(String key, E defaultValue) {
            Class<E> type = defaultValue.getDeclaringClass();
            return add(key, defaultValue, null, null, type, raw -> {
                if (!(raw instanceof String s)) {
                    return null;
                }
                for (E constant : type.getEnumConstants()) {
                    if (constant.name().equalsIgnoreCase(s)) {
                        return constant;
                    }
                }
                return null;
            });
        }

        private <T> Value<T> add(String key, T defaultValue, T min, T max, Class<T> type, Function<Object, T> convert) {
            if (min != null && ((Comparable<T>) min).compareTo(max) > 0) {
                throw new IllegalArgumentException(key + ": min is above max");
            }
            Value<T> value = new Value<>(String.join(".", tables), key, pendingComments, defaultValue, min, max, type, convert);
            if (!value.inRange(defaultValue)) {
                throw new IllegalArgumentException(key + ": default " + defaultValue + " is outside its range");
            }
            for (Value<?> existing : values) {
                if (existing.path().equals(value.path())) {
                    throw new IllegalArgumentException("Two settings at " + value.path());
                }
            }
            pendingComments = new ArrayList<>();
            values.add(value);
            return value;
        }

        /** Loads {@code config/<modid>.toml} from the loader's config folder and, in the game client, adds the settings screen. */
        public ModConfig build() {
            ModConfig config = build(Hooks.platform().configDir());
            if (Modtrix.isClient()) {
                Hooks.client().registerConfigScreen(config);
            }
            return config;
        }

        /** Loads the config from {@code directory} without touching the game; for tests and tools. */
        public ModConfig build(Path directory) {
            if (!tables.isEmpty()) {
                throw new IllegalStateException("push(\"" + tables.get(tables.size() - 1) + "\") was never popped");
            }
            ModConfig config = new ModConfig(modId, directory.resolve(modId + ".toml"), new ArrayList<>(values));
            config.load();
            synchronized (BY_MOD) {
                BY_MOD.put(modId, config);
            }
            return config;
        }
    }
}
