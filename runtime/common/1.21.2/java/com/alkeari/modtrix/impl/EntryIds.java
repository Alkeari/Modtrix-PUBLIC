package com.alkeari.modtrix.impl;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** From 1.21.2 an item or block reads its id from its properties while it is constructed. */
public final class EntryIds {
    private EntryIds() {
    }

    public static Item.Properties item(Item.Properties properties, ResourceKey<Item> key) {
        return properties.setId(key);
    }

    public static BlockBehaviour.Properties block(BlockBehaviour.Properties properties, ResourceKey<Block> key) {
        return properties.setId(key);
    }
}
