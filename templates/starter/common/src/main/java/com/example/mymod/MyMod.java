package com.example.mymod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MyMod {
    public static final String MOD_ID = "mymod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private MyMod() {
    }

    public static void init() {
        LOGGER.info("{} loaded", MOD_ID);
    }
}
