package com.slyph.clovergraves.storage;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class ItemSerialization {

    private ItemSerialization() {
    }

    public static byte @NotNull [] serialize(ItemStack @NotNull [] items) {
        return ItemStack.serializeItemsAsBytes(Arrays.asList(items));
    }

    public static ItemStack @NotNull [] deserialize(byte @NotNull [] data) {
        return ItemStack.deserializeItemsFromBytes(data);
    }
}
