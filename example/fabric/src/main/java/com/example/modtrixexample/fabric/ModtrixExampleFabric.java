package com.example.modtrixexample.fabric;

import com.example.modtrixexample.ModtrixExample;
import net.fabricmc.api.ModInitializer;

public class ModtrixExampleFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ModtrixExample.init();
    }
}
