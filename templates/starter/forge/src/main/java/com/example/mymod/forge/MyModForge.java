package com.example.mymod.forge;

import com.example.mymod.MyMod;
import net.minecraftforge.fml.common.Mod;

@Mod(MyMod.MOD_ID)
public class MyModForge {
    public MyModForge() {
        MyMod.init();
    }
}
