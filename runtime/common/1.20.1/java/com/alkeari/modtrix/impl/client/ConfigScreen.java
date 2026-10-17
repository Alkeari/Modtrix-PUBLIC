package com.alkeari.modtrix.impl.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.alkeari.modtrix.api.ModConfig;
import com.alkeari.modtrix.api.ModConfig.Value;
import com.alkeari.modtrix.impl.Hooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Every setting of one {@link ModConfig}, a page at a time, built only from vanilla widgets so it looks the same on every
 * loader. Edits apply when Done is pressed; a value that is not valid turns its box red and holds Done back.
 */
public final class ConfigScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFD700;
    private static final int ROW = 24;

    private final Screen parent;
    private final ModConfig config;
    private final List<Row> rows = new ArrayList<>();
    private final Map<Value<?>, String> pending = new LinkedHashMap<>();
    private final List<EditBox> invalid = new ArrayList<>();
    private int page;
    private Button done;

    private record Row(String header, Value<?> value) {
    }

    public ConfigScreen(Screen parent, ModConfig config) {
        super(Component.translatableWithFallback(config.modId() + ".config.title", displayName(config.modId()) + " Settings"));
        this.parent = parent;
        this.config = config;
        String table = "";
        for (Value<?> value : config.values()) {
            String path = value.path();
            String valueTable = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : "";
            if (!valueTable.equals(table)) {
                table = valueTable;
                if (!table.isEmpty()) {
                    rows.add(new Row(table, null));
                }
            }
            rows.add(new Row(null, value));
            pending.put(value, text(value));
        }
    }

    private int perPage() {
        return Math.max(1, (height - 72) / ROW);
    }

    private int pages() {
        return Math.max(1, (rows.size() + perPage() - 1) / perPage());
    }

    @Override
    protected void init() {
        invalid.clear();
        page = Math.min(page, pages() - 1);
        int right = width / 2 + 5;
        int y = 32;
        int first = page * perPage();
        for (int i = first; i < Math.min(rows.size(), first + perPage()); i++, y += ROW) {
            Row row = rows.get(i);
            if (row.value() != null) {
                addRenderableWidget(editor(row.value(), right, y));
            }
        }
        int bottom = height - 28;
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(width / 2 - 155, bottom, 20, 20).build());
        previous.setTooltip(Tooltip.create(Component.literal("Shows the previous page of settings.")));
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(width / 2 - 131, bottom, 20, 20).build());
        next.setTooltip(Tooltip.create(Component.literal("Shows the next page of settings.")));
        next.active = page < pages() - 1;
        Button reset = addRenderableWidget(Button.builder(Component.literal("Reset Page"), b -> resetPage()).bounds(width / 2 - 107, bottom, 70, 20).build());
        reset.setTooltip(Tooltip.create(Component.literal("Puts every setting on this page back to its default; nothing is saved until Done.")));
        Button cancel = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width / 2 - 33, bottom, 92, 20).build());
        cancel.setTooltip(Tooltip.create(Component.literal("Closes without saving any change.")));
        done = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> save()).bounds(width / 2 + 63, bottom, 92, 20).build());
        done.setTooltip(Tooltip.create(Component.literal("Saves every change to " + config.file().getFileName() + " and closes.")));
        refreshDone();
    }

    private AbstractWidget editor(Value<?> value, int x, int y) {
        Tooltip tooltip = Tooltip.create(tooltip(value));
        if (value.type() == Boolean.class) {
            CycleButton<Boolean> button = CycleButton.onOffBuilder(Boolean.parseBoolean(pending.get(value))).displayOnlyValue()
                    .create(x, y, 150, 20, label(value), (b, on) -> pending.put(value, on.toString()));
            button.setTooltip(tooltip);
            return button;
        }
        if (value.type().isEnum()) {
            CycleButton<String> button = ScreenCompat.cycle(name -> Component.literal(humanize(name.toLowerCase())), value.enumNames(), pending.get(value))
                    .displayOnlyValue()
                    .create(x, y, 150, 20, label(value), (b, name) -> pending.put(value, name));
            button.setTooltip(tooltip);
            return button;
        }
        EditBox box = new EditBox(font, x + 1, y + 1, 148, 18, label(value));
        box.setMaxLength(256);
        box.setValue(pending.get(value));
        box.setTooltip(tooltip);
        box.setResponder(textValue -> {
            pending.put(value, textValue);
            mark(box, value.accepts(textValue));
        });
        mark(box, value.accepts(box.getValue()));
        return box;
    }

    private void mark(EditBox box, boolean ok) {
        box.setTextColor(ok ? 0xE0E0E0 : 0xFF5555);
        if (ok) {
            invalid.remove(box);
        } else if (!invalid.contains(box)) {
            invalid.add(box);
        }
        refreshDone();
    }

    private void refreshDone() {
        if (done != null) {
            done.active = invalid.isEmpty() && pending.entrySet().stream().allMatch(e -> e.getKey().accepts(e.getValue()));
        }
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(pages() - 1, page + delta));
        rebuild();
    }

    private void resetPage() {
        int first = page * perPage();
        for (int i = first; i < Math.min(rows.size(), first + perPage()); i++) {
            Value<?> value = rows.get(i).value();
            if (value != null) {
                pending.put(value, defaultText(value));
            }
        }
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void save() {
        pending.forEach(Value::setFromText);
        config.save();
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ScreenCompat.background(this, graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, WHITE);
        int left = width / 2 - 155;
        int y = 32;
        int first = page * perPage();
        for (int i = first; i < Math.min(rows.size(), first + perPage()); i++, y += ROW) {
            Row row = rows.get(i);
            if (row.value() == null) {
                graphics.drawString(font, header(row.header()), left, y + 6, GOLD);
            } else {
                graphics.drawString(font, label(row.value()), left + 8, y + 6, WHITE);
            }
        }
        if (pages() > 1) {
            graphics.drawCenteredString(font, Component.literal("Page " + (page + 1) + " of " + pages()), width / 2, height - 40, WHITE);
        }
    }

    private Component label(Value<?> value) {
        return Component.translatableWithFallback(config.modId() + ".config." + value.path(), humanize(value.key()));
    }

    private Component header(String table) {
        String last = table.contains(".") ? table.substring(table.lastIndexOf('.') + 1) : table;
        return Component.translatableWithFallback(config.modId() + ".config." + table, humanize(last));
    }

    private Component tooltip(Value<?> value) {
        StringBuilder fallback = new StringBuilder(String.join(" ", value.comments()));
        if (value.min() != null) {
            fallback.append(fallback.length() > 0 ? " " : "").append("Range: ").append(value.min()).append(" to ").append(value.max()).append('.');
        }
        fallback.append(fallback.length() > 0 ? " " : "").append("Default: ").append(defaultText(value)).append('.');
        return Component.translatableWithFallback(config.modId() + ".config." + value.path() + ".tooltip", fallback.toString());
    }

    private static String text(Value<?> value) {
        Object current = value.get();
        return current instanceof Enum<?> e ? e.name() : String.valueOf(current);
    }

    private static String defaultText(Value<?> value) {
        Object d = value.defaultValue();
        return d instanceof Enum<?> e ? e.name() : String.valueOf(d);
    }

    /** The name the mod's metadata gives, which the loader's mod list shows too; the id reworded when there is none. */
    private static String displayName(String modId) {
        String name = Hooks.platform().modName(modId);
        return name == null || name.isBlank() ? humanize(modId) : name;
    }

    static String humanize(String key) {
        StringBuilder out = new StringBuilder();
        for (String word : key.split("[_\\s]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
