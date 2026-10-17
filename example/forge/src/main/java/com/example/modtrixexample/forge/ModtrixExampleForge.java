package com.example.modtrixexample.forge;

import com.example.modtrixexample.ModtrixExample;
import net.minecraftforge.fml.common.Mod;

@Mod(ModtrixExample.MOD_ID)
public class ModtrixExampleForge {
    public ModtrixExampleForge() {
        ModtrixExample.init();
    }
}
