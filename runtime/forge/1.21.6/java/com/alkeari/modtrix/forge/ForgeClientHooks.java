package com.alkeari.modtrix.forge;

import com.alkeari.modtrix.api.ModConfig;
import com.alkeari.modtrix.impl.ClientHooks;
import com.alkeari.modtrix.impl.client.ConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import com.alkeari.modtrix.impl.MessageSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.PacketDistributor;

public final class ForgeClientHooks implements ClientHooks {
    @Override
    public void onClientTick(Runnable listener) {
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> listener.run());
    }

    @Override
    public <T> void registerReceiver(MessageSpec<T> spec) {
        // The channel registration in ForgePlatformHooks already routes this type on both sides.
    }

    @Override
    public <T> void sendToServer(MessageSpec<T> spec, T message) {
        ForgePlatformHooks.instance().channel(spec.id().getNamespace()).send(message, PacketDistributor.SERVER.noArg());
    }

    @Override
    public void registerConfigScreen(ModConfig config) {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ConfigScreen(parent, config)));
    }
}
