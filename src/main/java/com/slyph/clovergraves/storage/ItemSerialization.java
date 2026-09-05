package com.slyph.clovergraves.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;

public final class ItemSerialization {
    private static final int MAGIC = 0x43475231;

    private ItemSerialization() {
    }

    public static byte[] serialize(ItemStack[] items) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(items.length);
                for (ItemStack item : items) {
                    output.writeObject(item);
                }
            }
            return bytes.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize grave items", ex);
        }
    }

    public static ItemStack[] deserialize(byte[] data) {
        try {
            try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
                if (input.readInt() != MAGIC) throw new IllegalStateException("Unknown CloverGraves item format");
                int length = input.readInt();
                if (length < 0 || length > 1000) throw new IllegalStateException("Invalid item array length " + length);

                ItemStack[] items = new ItemStack[length];
                for (int i = 0; i < length; i++) {
                    Object value = input.readObject();
                    items[i] = value instanceof ItemStack item ? item : null;
                }
                return items;
            }
        } catch (Exception primary) {
            ItemStack[] legacy = deserializePaperLegacy(data);
            if (legacy != null) return legacy;
            throw new IllegalStateException("Failed to deserialize grave items", primary);
        }
    }

    private static ItemStack[] deserializePaperLegacy(byte[] data) {
        try {
            Method method = ItemStack.class.getMethod("deserializeItemsFromBytes", byte[].class);
            Object result = method.invoke(null, data);
            return result instanceof ItemStack[] items ? items : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
