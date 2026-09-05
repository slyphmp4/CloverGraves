package com.slyph.clovergraves.listeners;

import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.SpawnedGraves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.jetbrains.annotations.NotNull;

public class GraveEntityInteractListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(@NotNull PlayerInteractEntityEvent event) {
        Grave grave = SpawnedGraves.getGrave(event.getRightClicked().getUniqueId());
        if (grave == null) return;

        event.setCancelled(true);
        grave.interact(event.getPlayer(), event.getHand());
    }
}
