package com.slyph.clovergraves.listeners;

import com.slyph.clovergraves.grave.BlockKey;
import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.SpawnedGraves;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerInteractListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null || event.getHand() == null) return;

        Location location = clicked.getLocation();
        Grave grave = SpawnedGraves.getGrave(new BlockKey(location.getWorld().getUID(), clicked.getX(), clicked.getY(), clicked.getZ()));
        if (grave == null) return;

        event.setUseInteractedBlock(Event.Result.DENY);
        grave.interact(event.getPlayer(), event.getHand());
    }
}
