package com.alkeari.modtrix.impl.client;

import com.alkeari.modtrix.impl.UpdateCheck;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Tells the player in chat, once they are in a world, that a newer version of the mod is out; silent otherwise. */
public final class UpdateNotice {
    private UpdateNotice() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        UpdateCheck.Notice notice = UpdateCheck.take();
        if (notice == null) {
            return;
        }
        Component link = Component.literal(notice.page()).withStyle(ChatCompat.link(notice.page()).withColor(ChatFormatting.AQUA).withUnderlined(true));
        ChatCompat.show(minecraft.player, Component.literal(notice.name() + " " + notice.latest() + " is out (you have " + notice.current() + "): ")
                .withStyle(ChatFormatting.GOLD).append(link));
    }
}
