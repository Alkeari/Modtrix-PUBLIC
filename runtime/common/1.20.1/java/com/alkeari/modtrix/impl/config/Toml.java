package com.alkeari.modtrix.impl.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The part of TOML a mod config needs: comments, dotted table headers, and keys holding a boolean, an integer, a float or
 * a basic string. Values are read as flat dotted paths ({@code climate.globe_size}) so a config never depends on nesting.
 */
public final class Toml {
    private Toml() {
    }

    /** Reads every {@code key = value} under its table, as dotted path to value; lines it cannot read are reported, never fatal. */
    public static Map<String, Object> read(String text, List<String> problems) {
        Map<String, Object> values = new LinkedHashMap<>();
        String table = "";
        String[] lines = text.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]") && !line.startsWith("[[")) {
                table = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                problems.add("line " + (i + 1) + ": expected key = value, found \"" + lines[i].trim() + "\"");
                continue;
            }
            String key = line.substring(0, eq).trim();
            String raw = line.substring(eq + 1).trim();
            Object value = parseValue(raw);
            if (value == null) {
                problems.add("line " + (i + 1) + ": cannot read the value of " + key + ": " + raw);
                continue;
            }
            values.put(table.isEmpty() ? key : table + "." + key, value);
        }
        return values;
    }

    /** A boolean, a long, a double or a string, or null when {@code raw} is none of them. */
    public static Object parseValue(String raw) {
        if (raw.equals("true")) {
            return Boolean.TRUE;
        }
        if (raw.equals("false")) {
            return Boolean.FALSE;
        }
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return unescape(raw.substring(1, raw.length() - 1));
        }
        String number = raw.replace("_", "");
        try {
            if (number.matches("[+-]?\\d+")) {
                return Long.parseLong(number);
            }
            if (number.matches("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")) {
                return Double.parseDouble(number);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && quoted) {
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == '#' && !quoted) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 == s.length()) {
                out.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                default -> out.append(n);
            }
        }
        return out.toString();
    }

    public static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }

    /** One table's worth of output: its header path (empty for the top) and its commented entries, in declaration order. */
    public record Table(String path, List<Entry> entries) {
        public Table(String path) {
            this(path, new ArrayList<>());
        }
    }

    public record Entry(String key, List<String> comments, String value) {
    }

    public static String write(List<Table> tables) {
        StringBuilder out = new StringBuilder();
        for (Table table : tables) {
            if (table.entries().isEmpty()) {
                continue;
            }
            if (!table.path().isEmpty()) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append('[').append(table.path()).append("]\n");
            }
            for (Entry entry : table.entries()) {
                for (String comment : entry.comments()) {
                    out.append("# ").append(comment).append('\n');
                }
                out.append(entry.key()).append(" = ").append(entry.value()).append("\n\n");
            }
        }
        return out.toString().stripTrailing() + "\n";
    }
}
