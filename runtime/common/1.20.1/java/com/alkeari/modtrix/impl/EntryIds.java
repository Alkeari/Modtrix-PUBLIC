package com.alkeari.modtrix.impl;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Gives an item's or block's properties its registry id where Minecraft needs it before construction; it does not before 1.21.2. */
public final class EntryIds {
    private EntryIds() {
    }

    public static Item.Properties item(Item.Properties properties, ResourceKey<Item> key) {
        return properties;
    }

    public static BlockBehaviour.Properties block(BlockBehaviour.Properties properties, ResourceKey<Block> key) {
        return properties;
    }
}
