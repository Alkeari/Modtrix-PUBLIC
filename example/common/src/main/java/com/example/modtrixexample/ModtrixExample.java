package com.example.modtrixexample;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import com.alkeari.modtrix.api.Events;
import com.alkeari.modtrix.api.MessageType;
import com.alkeari.modtrix.api.ModConfig;
import com.alkeari.modtrix.api.Modtrix;
import com.alkeari.modtrix.api.Network;
import com.alkeari.modtrix.api.Registrar;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Uses every part of the Modtrix runtime once and logs whether each one worked. */
public final class ModtrixExample {
    public static final String MOD_ID = "modtrixexample";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static boolean commandsRegistered;
    private static boolean checked;
    private static Supplier<Item> widget;
    static ModConfig config;
    static ModConfig.Value<Integer> pingValue;
    static volatile int receivedPing = -1;
    public static MessageType<Ping> ping;
    public static MessageType<Pong> pong;

    public record Ping(int value) {
    }

    public record Pong(int value) {
    }

    private ModtrixExample() {
    }

    public static void init() {
        LOGGER.info("Modtrix example starting on {} (client: {})", Modtrix.loader(), Modtrix.isClient());
        ModConfig.Builder settings = ModConfig.builder(MOD_ID);
        settings.push("network");
        pingValue = settings.comment("Number the server sends in its ping to each joining player.").defineInt("ping_value", 42, 0, 1000);
        settings.pop();
        settings.comment("Logs every received message.").define("verbose", true);
        settings.checkForUpdates(System.getProperty("modtrixexample.update_url", "https://example.com/modtrixexample/updates.json"));
        config = settings.build();
        widget = Registrar.item(Registrar.create(MOD_ID, Registries.ITEM), "widget", Item::new);
        ping = Network.toClient(id("ping"), Ping.class,
                (message, buf) -> buf.writeVarInt(message.value()), buf -> new Ping(buf.readVarInt()),
                message -> {
                    LOGGER.info("Modtrix example: client received ping {}", message.value());
                    receivedPing = message.value();
                    pong.sendToServer(new Pong(message.value()));
                });
        pong = Network.toServer(id("pong"), Pong.class,
                (message, buf) -> buf.writeVarInt(message.value()), buf -> new Pong(buf.readVarInt()),
                (message, player) -> LOGGER.info("Modtrix example: server received pong {} from {}", message.value(), player.getName().getString()));
        Events.onRegisterCommands((dispatcher, context, selection) -> {
            dispatcher.register(Commands.literal(MOD_ID).then(Commands.literal("ping")
                    .then(Commands.argument("value", IntegerArgumentType.integer()).executes(command -> {
                        ping.sendTo(command.getSource().getPlayerOrException(), new Ping(IntegerArgumentType.getInteger(command, "value")));
                        return 1;
                    }))));
            commandsRegistered = true;
        });
        Events.onServerTick(server -> {
            if (!checked) {
                checked = true;
                selfCheck();
            }
        });
        Events.onPlayerJoin(player -> {
            LOGGER.info("Modtrix example: {} joined", player.getName().getString());
            ping.sendTo(player, new Ping(pingValue.get()));
        });
        Events.onServerStopping(server -> LOGGER.info("Modtrix example: server stopping"));
        Modtrix.onClient(() -> Events.onClientTick(ClientCheck::tick));
    }

    private static void selfCheck() {
        List<String> failures = new ArrayList<>();
        if (!java.nio.file.Files.isRegularFile(config.file()) || !config.file().getFileName().toString().equals(MOD_ID + ".toml")) {
            failures.add("config file " + config.file() + " was not written");
        }
        if (ModConfig.only() != config || pingValue.get() < 0) {
            failures.add("config is not registered or its value is invalid");
        }
        if (!commandsRegistered) {
            failures.add("commands were never registered");
        }
        if (!id("widget").equals(BuiltInRegistries.ITEM.getKey(widget.get()))) {
            failures.add("widget item is not registered as " + id("widget"));
        }
        if (!Modtrix.isModLoaded(MOD_ID)) {
            failures.add("isModLoaded does not see this mod");
        }
        if (MixinCheck.ticks() < 0) {
            failures.add("the MinecraftServer mixin never ran");
        }
        String runtimePackage = Modtrix.class.getPackageName();
        if (runtimePackage.startsWith("com.alkeari.modtrix")) {
            failures.add("runtime was not relocated: " + runtimePackage);
        }
        if (failures.isEmpty()) {
            LOGGER.info("Modtrix example self-check passed on {} with runtime in {}", Modtrix.loader(), runtimePackage);
        } else {
            LOGGER.error("Modtrix example self-check failed: {}", failures);
        }
    }

    //? if >=1.21.11 {
    /*static Identifier id(String path) {
        return Objects.requireNonNull(Identifier.tryBuild(MOD_ID, path));
    }
    *///?} else {
    static ResourceLocation id(String path) {
        return Objects.requireNonNull(ResourceLocation.tryBuild(MOD_ID, path));
    }
    //?}
}
