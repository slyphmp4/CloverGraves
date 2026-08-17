package com.artillexstudios.axgraves.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

/**
 * A copy of the slots that {@code grave-item-order} can prioritize, captured at death time
 * <strong>before</strong> anything clears the player's inventory. Grave used to read this
 * straight off {@code offlinePlayer.getPlayer().getInventory()} inside its constructor, which
 * ran after {@code override-keep-inventory} had already emptied that inventory - silently
 * turning "put armor/hand/offhand first" into a no-op.
 */
public record InventoryOrderSnapshot(ItemStack[] armor, ItemStack mainHand, ItemStack offHand) {

    public static final InventoryOrderSnapshot EMPTY = new InventoryOrderSnapshot(new ItemStack[0], null, null);

    @NotNull
    public static InventoryOrderSnapshot capture(@NotNull PlayerInventory inventory) {
        return new InventoryOrderSnapshot(
                inventory.getArmorContents(),
                inventory.getItemInMainHand(),
                inventory.getItemInOffHand()
        );
    }
}
