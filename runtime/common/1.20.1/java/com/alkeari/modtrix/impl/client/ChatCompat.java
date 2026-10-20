package com.alkeari.modtrix.impl.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Where the chat API changed shape between Minecraft versions; each variant holds one version's form. */
final class ChatCompat {
    private ChatCompat() {
    }

    /** A style that opens {@code url} when clicked. */
    static Style link(String url) {
        return Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
    }

    /** Adds {@code message} to the player's chat. */
    static void show(LocalPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }
}
