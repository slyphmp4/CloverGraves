package com.slyph.clovergraves.storage;

import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public final class ItemSerialization {

    private ItemSerialization() {
    }

    public static byte[] serialize(ItemStack[] items) {
        return ItemStack.serializeItemsAsBytes(Arrays.asList(items));
    }

    public static ItemStack[] deserialize(byte[] data) {
        return ItemStack.deserializeItemsFromBytes(data);
    }
}
