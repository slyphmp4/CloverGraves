package com.artillexstudios.axgraves.utils;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;

public class InventoryUtils {
    /** A Bukkit inventory tops out at 6 rows (54 slots); {@link #getRequiredRows(int)} clamps to it. */
    public static final int MAX_ROWS = 6;
    public static final int MAX_SLOTS = MAX_ROWS * 9;

    @NotNull
    public static List<ItemStack> reorderInventory(@NotNull InventoryOrderSnapshot snapshot, @NotNull List<ItemStack> keptItems) {
        List<ItemStack> priority = new ArrayList<>();

        for (String str : CONFIG.getStringList("grave-item-order")) {
            switch (str) {
                case "ARMOR" -> {
                    for (ItemStack it : snapshot.armor()) {
                        if (it != null) priority.add(it);
                    }
                }
                case "HAND" -> priority.add(snapshot.mainHand());
                case "OFFHAND" -> priority.add(snapshot.offHand());
                default -> {
                }
            }
        }

        return ItemOrdering.reorder(keptItems, priority);
    }

    /**
     * Rows required to fit {@code amount} items, clamped to {@link #MAX_ROWS}. Previously
     * unclamped: more than 54 stacks (e.g. from another plugin adding drops) made
     * {@code Bukkit.createInventory} throw {@code IllegalArgumentException} with no
     * surrounding try/catch, wiping the dying player's entire inventory.
     */
    public static int getRequiredRows(int amount) {
        if (amount <= 0) return 1;
        int rows = amount / 9;
        if (amount % 9 != 0) rows++;
        return Math.min(Math.max(rows, 1), MAX_ROWS);
    }
}
