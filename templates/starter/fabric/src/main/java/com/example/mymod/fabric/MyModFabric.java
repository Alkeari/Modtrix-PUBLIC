package com.example.mymod.fabric;

import com.example.mymod.MyMod;
import net.fabricmc.api.ModInitializer;

public class MyModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        MyMod.init();
    }
}
