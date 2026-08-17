package com.artillexstudios.axgraves.listeners;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.GraveInventoryHolder;
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

/**
 * Grave GUIs used to be created with a {@code null} holder and had no click/drag listener at
 * all, so any player could deposit items into any grave - their own or a stranger's - and use it
 * as free storage; with {@code drop-items: false}, whatever they stashed was simply deleted once
 * the grave expired. This locks the top inventory down to withdraw-only, and keeps
 * {@link com.artillexstudios.axgraves.grave.GraveContents} in sync with what actually happened
 * to the live Bukkit inventory after each accepted click/drag/close.
 */
public class GraveInventoryListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onClick(@NotNull InventoryClickEvent event) {
        Grave grave = graveOf(event.getView());
        if (grave == null) return;

        int topSize = event.getView().getTopInventory().getSize();
        boolean clickedTop = event.getRawSlot() < topSize;
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

        syncSoon(grave);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(@NotNull InventoryDragEvent event) {
        Grave grave = graveOf(event.getView());
        if (grave == null) return;

        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (touchesTop) {
            event.setCancelled(true);
            return;
        }

        syncSoon(grave);
    }

    @EventHandler
    public void onClose(@NotNull InventoryCloseEvent event) {
        Grave grave = graveOf(event.getView());
        if (grave == null) return;

        Scheduler.get().runAt(grave.getLocation(), task -> {
            grave.contents().syncFromView();
            grave.contents().closeViewIfEmpty();
        });
    }

    private void syncSoon(@NotNull Grave grave) {
        // resync one tick later, so we read the inventory state *after* the click resolves
        Scheduler.get().runLaterAt(grave.getLocation(), task -> grave.contents().syncFromView(), 1L);
    }

    @Nullable
    private Grave graveOf(@NotNull InventoryView view) {
        InventoryHolder holder = view.getTopInventory().getHolder();
        if (!(holder instanceof GraveInventoryHolder graveHolder)) return null;
        return graveHolder.grave();
    }
}
