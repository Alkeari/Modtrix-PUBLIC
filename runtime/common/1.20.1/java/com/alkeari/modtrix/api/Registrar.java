package com.alkeari.modtrix.api;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import com.alkeari.modtrix.impl.EntryIds;
import com.alkeari.modtrix.impl.Hooks;
import com.alkeari.modtrix.impl.RegistrarBackend;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Registers entries into one game registry at the time each loader expects. Create it and register every entry during
 * mod initialization; the supplier it returns gives the entry once the registry has it.
 */
public final class Registrar<T> {
    private final String modId;
    private final RegistrarBackend<T> backend;

    private Registrar(String modId, RegistrarBackend<T> backend) {
        this.modId = modId;
        this.backend = backend;
    }

    public static <T> Registrar<T> create(String modId, ResourceKey<? extends Registry<T>> registry) {
        return new Registrar<>(modId, Hooks.platform().registrar(modId, registry));
    }

    public <E extends T> Supplier<E> register(String name, Supplier<E> factory) {
        return backend.register(name, factory);
    }

    /**
     * Registers an item built from properties that already carry its id, which every Minecraft version accepts and 1.21.2
     * and later require; use it for items instead of {@link #register}.
     */
    public static <I extends Item> Supplier<I> item(Registrar<Item> items, String name, Function<Item.Properties, I> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, items.id(name));
        return items.register(name, () -> factory.apply(EntryIds.item(new Item.Properties(), key)));
    }

    /** As {@link #item}, for blocks. */
    public static <B extends Block> Supplier<B> block(Registrar<Block> blocks, String name, Function<BlockBehaviour.Properties, B> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, blocks.id(name));
        return blocks.register(name, () -> factory.apply(EntryIds.block(BlockBehaviour.Properties.of(), key)));
    }

    private ResourceLocation id(String name) {
        return Objects.requireNonNull(ResourceLocation.tryBuild(modId, name), () -> "Not a valid id: " + modId + ":" + name);
    }
}
