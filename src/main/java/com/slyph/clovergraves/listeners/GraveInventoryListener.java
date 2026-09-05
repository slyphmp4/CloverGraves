package com.slyph.clovergraves.listeners;

import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.GraveInventoryHolder;
import com.slyph.clovergraves.schedulers.CloverScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GraveInventoryListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onClick(@NotNull InventoryClickEvent event) {
        Grave grave = graveOf(event.getView());
        if (grave == null) return;

        int topSize = event.getView().getTopInventory().getSize();
        boolean clickedTop = event.getRawSlot() >= 0 && event.getRawSlot() < topSize;
        InventoryAction action = event.getAction();

        boolean deposits = clickedTop && switch (action) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> true;
            default -> false;
        };
        deposits |= !clickedTop && action == InventoryAction.MOVE_TO_OTHER_INVENTORY;

        if (deposits) {
            event.setCancelled(true);
            return;
        }

        Player looter = event.getWhoClicked() instanceof Player player ? player : null;
        syncSoon(grave, looter);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(@NotNull InventoryDragEvent event) {
        Grave grave = graveOf(event.getView());
        if (grave == null) return;

        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topSize)) {
            event.setCancelled(true);
            return;
        }

        Player looter = event.getWhoClicked() instanceof Player player ? player : null;
        syncSoon(grave, looter);
    }

    @EventHandler
    public void onClose(@NotNull InventoryCloseEvent event) {
        Grave grave = graveOf(event.getView());
        if (grave == null) return;

        Player looter = event.getPlayer() instanceof Player player ? player : null;
        CloverScheduler.get().runAt(grave.getLocation(), () -> {
            grave.syncFromView(looter);
            grave.contents().closeViewIfEmpty();
        });
    }

    private void syncSoon(@NotNull Grave grave, @Nullable Player looter) {
        CloverScheduler.get().runLaterAt(grave.getLocation(), task -> grave.syncFromView(looter), 1L);
    }

    @Nullable
    private Grave graveOf(@NotNull InventoryView view) {
        InventoryHolder holder = view.getTopInventory().getHolder();
        if (!(holder instanceof GraveInventoryHolder graveHolder)) return null;
        return graveHolder.grave();
    }
}
