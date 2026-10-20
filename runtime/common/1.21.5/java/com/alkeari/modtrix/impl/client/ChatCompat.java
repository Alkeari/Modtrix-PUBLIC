package com.alkeari.modtrix.impl.client;

import java.net.URI;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Where the chat API changed shape between Minecraft versions; each variant holds one version's form. */
final class ChatCompat {
    private ChatCompat() {
    }

    /** From 1.21.5 each click action is its own record, and opening a link takes a URI. */
    static Style link(String url) {
        return Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)));
    }

    /** Adds {@code message} to the player's chat. */
    static void show(LocalPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }
}
