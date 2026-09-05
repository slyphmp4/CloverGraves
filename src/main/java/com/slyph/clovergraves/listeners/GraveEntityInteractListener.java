package com.slyph.clovergraves.listeners;

import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.SpawnedGraves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

public class GraveEntityInteractListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(@NotNull PlayerInteractEntityEvent event) {
        Grave grave = SpawnedGraves.getGrave(event.getRightClicked().getUniqueId());
        if (grave == null) return;

        boolean alreadyCancelled = event.isCancelled();
        event.setCancelled(true);
        if (alreadyCancelled) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        grave.interact(event.getPlayer(), EquipmentSlot.HAND);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onManipulate(@NotNull PlayerArmorStandManipulateEvent event) {
        Grave grave = SpawnedGraves.getGrave(event.getRightClicked().getUniqueId());
        if (grave == null) return;

        boolean alreadyCancelled = event.isCancelled();
        event.setCancelled(true);
        if (alreadyCancelled) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        grave.interact(event.getPlayer(), EquipmentSlot.HAND);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(@NotNull EntityDamageEvent event) {
        if (SpawnedGraves.getGrave(event.getEntity().getUniqueId()) == null) return;
        event.setCancelled(true);
    }
}
