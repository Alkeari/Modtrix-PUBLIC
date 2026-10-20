package com.alkeari.modtrix.impl.client;

import java.util.List;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Where the screen API changed shape between Minecraft versions; each variant holds one version's form. */
final class ScreenCompat {
    private ScreenCompat() {
    }

    /** From 1.20.2 {@link Screen#render} draws the background itself. */
    static void background(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    /** From 1.21.11 a cycle button takes its initial value in {@code builder}. */
    static CycleButton.Builder<String> cycle(Function<String, Component> label, List<String> values, String initial) {
        return CycleButton.builder(label, initial).withValues(values);
    }
}
