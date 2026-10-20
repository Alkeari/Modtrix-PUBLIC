package com.example.modtrixexample.mixin;

import com.example.modtrixexample.MixinCheck;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reads a shadowed field from an injected method, so the self-check proves both kinds of mixin member apply. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Shadow
    private int tickCount;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void modtrixexample$tick(BooleanSupplier hasTimeLeft, CallbackInfo info) {
        MixinCheck.tick(tickCount);
    }
}
