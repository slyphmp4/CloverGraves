package com.slyph.clovergraves.grave;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies a grave's GUI to listeners in O(1) via {@code Inventory#getHolder()} - previously
 * the inventory was created with a {@code null} holder, so nothing could tell "this inventory
 * belongs to grave X" without an O(n) scan, and there was no way to distinguish it from any other
 * inventory in {@code InventoryClickEvent}/{@code InventoryDragEvent}, which is exactly what let
 * players deposit items into a grave (see {@link com.slyph.clovergraves.listeners.GraveInventoryListener}).
 */
public final class GraveInventoryHolder implements InventoryHolder {
    private final Grave grave;
    private Inventory inventory;

    public GraveInventoryHolder(@NotNull Grave grave) {
        this.grave = grave;
    }

    @NotNull
    public Grave grave() {
        return grave;
    }

    void bind(@NotNull Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("grave inventory has not been opened yet");
        return inventory;
    }
}
