package com.example.modtrixexample;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=1.21.9 {
/*import net.minecraft.client.input.KeyEvent;
*///?}

/**
 * Client only: confirms the client tick hook fires and, once a player is in a world, opens this mod's settings screen
 * through the same lookup the loader's mod list uses, saves a screenshot of it, changes the ping value through its
 * text box, presses Done and reads the value back from memory and from the file.
 */
final class ClientCheck {
    private static final int OPEN = 60;
    private static final int CAPTURE = 80;
    private static final int EDIT = 100;
    private static final int VERIFY = 120;

    private static int ticks;
    private static int inWorld;
    private static Screen settings;
    private static int edited;
    private static final List<String> failures = new ArrayList<>();

    private ClientCheck() {
    }

    static void tick() {
        if (++ticks == 100) {
            ModtrixExample.LOGGER.info("Modtrix example: client tick hook reached 100 ticks");
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || inWorld > VERIFY) {
            return;
        }
        inWorld++;
        try {
            if (inWorld == OPEN) {
                open(minecraft);
            } else if (inWorld == CAPTURE && settings != null) {
                //? if >=26.2 {
                /*Screenshot.grab(minecraft.gameDirectory, minecraft.gameRenderer.mainRenderTarget(),
                *///?} else
                Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(),
                        message -> ModtrixExample.LOGGER.info("Modtrix example: settings screen captured: {}", message.getString()));
            } else if (inWorld == EDIT && settings != null) {
                edit();
            } else if (inWorld == VERIFY) {
                verify(minecraft);
            }
        } catch (Exception e) {
            failures.add(e.toString());
            ModtrixExample.LOGGER.error("Modtrix example client check failed: {}", failures, e);
            inWorld = VERIFY + 1;
        }
    }

    private static void open(Minecraft minecraft) throws ReflectiveOperationException {
        settings = SettingsScreenLookup.create(minecraft, current(minecraft));
        if (settings == null) {
            failures.add("the loader's mod list has no settings screen for " + ModtrixExample.MOD_ID);
            return;
        }
        show(minecraft, settings);
        ModtrixExample.LOGGER.info("Modtrix example: settings screen opened through the {} mod list lookup: {}",
                com.alkeari.modtrix.api.Modtrix.loader(), settings.getTitle().getString());
    }

    private static void edit() {
        String before = String.valueOf(ModtrixExample.pingValue.get());
        edited = ModtrixExample.pingValue.get() + 1;
        EditBox box = null;
        Button done = null;
        String doneLabel = Component.translatable("gui.done").getString();
        for (GuiEventListener child : settings.children()) {
            if (child instanceof EditBox candidate && candidate.getValue().equals(before)) {
                box = candidate;
            } else if (child instanceof Button button && button.getMessage().getString().equals(doneLabel)) {
                done = button;
            }
        }
        if (box == null || done == null) {
            failures.add("settings screen has no ping value box holding " + before + " or no Done button");
            return;
        }
        box.setValue(String.valueOf(edited));
        //? if >=1.21.9 {
        /*done.onPress(new KeyEvent(257, 0, 0));
        *///?} else
        done.onPress();
    }

    private static void verify(Minecraft minecraft) throws java.io.IOException {
        if (settings != null && edited != 0) {
            if (current(minecraft) == settings) {
                failures.add("Done did not close the settings screen");
            }
            if (ModtrixExample.pingValue.get() != edited) {
                failures.add("ping value is " + ModtrixExample.pingValue.get() + " after Done, not " + edited);
            }
            String file = Files.readString(ModtrixExample.config.file(), StandardCharsets.UTF_8);
            if (!file.contains("ping_value = " + edited)) {
                failures.add(ModtrixExample.config.file().getFileName() + " does not hold ping_value = " + edited);
            }
            ModtrixExample.config.load();
            if (ModtrixExample.pingValue.get() != edited) {
                failures.add("ping value is " + ModtrixExample.pingValue.get() + " after reloading the file, not " + edited);
            }
        }
        if (ModtrixExample.receivedPing < 0) {
            failures.add("no ping reached the client");
        }
        if (failures.isEmpty()) {
            ModtrixExample.LOGGER.info("Modtrix example client check passed: ping {} received, settings screen saved ping_value = {}",
                    ModtrixExample.receivedPing, edited);
        } else {
            ModtrixExample.LOGGER.error("Modtrix example client check failed: {}", failures);
        }
    }

    private static Screen current(Minecraft minecraft) {
        //? if >=26.2 {
        /*return minecraft.gui.screen();
        *///?} else
        return minecraft.screen;
    }

    private static void show(Minecraft minecraft, Screen screen) {
        //? if >=26.2 {
        /*minecraft.gui.setScreen(screen);
        *///?} else
        minecraft.setScreen(screen);
    }

    /** Finds the settings screen the way each loader's mod list does, by reflection so one source serves every loader. */
    private static final class SettingsScreenLookup {
        private SettingsScreenLookup() {
        }

        static Screen create(Minecraft minecraft, Screen parent) throws ReflectiveOperationException {
            return switch (com.alkeari.modtrix.api.Modtrix.loader()) {
                case "fabric" -> fabric(parent);
                case "forge" -> forge(minecraft, parent, "net.minecraftforge.fml.ModList", "net.minecraftforge.client.ConfigScreenHandler");
                default -> neoforge(minecraft, parent);
            };
        }

        /** Mod Menu reads the "modmenu" entrypoint of each mod and asks it for a config screen factory. */
        private static Screen fabric(Screen parent) throws ReflectiveOperationException {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            List<?> containers = (List<?>) loaderClass.getMethod("getEntrypointContainers", String.class, Class.class).invoke(loader, "modmenu", Object.class);
            Class<?> containerClass = Class.forName("net.fabricmc.loader.api.entrypoint.EntrypointContainer");
            Class<?> modClass = Class.forName("net.fabricmc.loader.api.ModContainer");
            Class<?> metadataClass = Class.forName("net.fabricmc.loader.api.metadata.ModMetadata");
            for (Object container : containers) {
                Object metadata = modClass.getMethod("getMetadata").invoke(containerClass.getMethod("getProvider").invoke(container));
                if (ModtrixExample.MOD_ID.equals(metadataClass.getMethod("getId").invoke(metadata))) {
                    Object api = containerClass.getMethod("getEntrypoint").invoke(container);
                    Object factory = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi").getMethod("getModConfigScreenFactory").invoke(api);
                    return (Screen) method(Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory"), "create", 1).invoke(factory, parent);
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private static Screen forge(Minecraft minecraft, Screen parent, String modList, String handler) throws ReflectiveOperationException {
            Object info = modInfo(modList);
            Optional<BiFunction<Minecraft, Screen, Screen>> factory = (Optional<BiFunction<Minecraft, Screen, Screen>>)
                    method(Class.forName(handler), "getScreenFactoryFor", 1).invoke(null, info);
            return factory.map(f -> f.apply(minecraft, parent)).orElse(null);
        }

        /** NeoForge 20.4 kept Forge's ConfigScreenHandler; from 20.5 the mod list asks the mod's IConfigScreenFactory. */
        private static Screen neoforge(Minecraft minecraft, Screen parent) throws ReflectiveOperationException {
            Class<?> factoryClass;
            try {
                factoryClass = Class.forName("net.neoforged.neoforge.client.gui.IConfigScreenFactory");
            } catch (ClassNotFoundException e) {
                return forge(minecraft, parent, "net.neoforged.fml.ModList", "net.neoforged.neoforge.client.ConfigScreenHandler");
            }
            Optional<?> factory = (Optional<?>) method(factoryClass, "getForMod", 1).invoke(null, modInfo("net.neoforged.fml.ModList"));
            if (factory.isEmpty()) {
                return null;
            }
            java.lang.reflect.Method create = method(factoryClass, "createScreen", 2);
            Object first = create.getParameterTypes()[0] == Minecraft.class ? minecraft : container("net.neoforged.fml.ModList");
            return (Screen) create.invoke(factory.get(), first, parent);
        }

        private static Object container(String modList) throws ReflectiveOperationException {
            Class<?> listClass = Class.forName(modList);
            java.lang.reflect.Method byId = listClass.getMethod("getModContainerById", String.class);
            // Forge 26.1 made ModList static and removed get().
            Object list = java.lang.reflect.Modifier.isStatic(byId.getModifiers()) ? null : listClass.getMethod("get").invoke(null);
            return ((Optional<?>) byId.invoke(list, ModtrixExample.MOD_ID)).orElseThrow();
        }

        private static Object modInfo(String modList) throws ReflectiveOperationException {
            Object container = container(modList);
            return method(Class.forName(modList.replace("ModList", "ModContainer")), "getModInfo", 0).invoke(container);
        }

        private static java.lang.reflect.Method method(Class<?> owner, String name, int parameters) throws NoSuchMethodException {
            for (java.lang.reflect.Method candidate : owner.getMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == parameters) {
                    return candidate;
                }
            }
            throw new NoSuchMethodException(owner.getName() + "." + name);
        }
    }
}
