package com.example.modtrixexample;

/** What the example's mixin saw, kept outside the mixin package because mixin classes cannot be referenced. */
public final class MixinCheck {
    private static volatile int ticks = -1;

    private MixinCheck() {
    }

    public static void tick(int tickCount) {
        ticks = tickCount;
    }

    /** The server's own tick count as the mixin last read it, or -1 when the mixin never ran. */
    public static int ticks() {
        return ticks;
    }
}
