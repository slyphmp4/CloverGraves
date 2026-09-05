package com.slyph.clovergraves.listeners;

import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.SpawnedGraves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.jetbrains.annotations.NotNull;

public class GraveEntityInteractListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(@NotNull PlayerInteractEntityEvent event) {
        Grave grave = SpawnedGraves.getGrave(event.getRightClicked().getUniqueId());
        if (grave == null) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(@NotNull EntityDamageEvent event) {
        Grave grave = SpawnedGraves.getGrave(event.getEntity().getUniqueId());
        if (grave == null) return;

        boolean alreadyCancelled = event.isCancelled();
        event.setCancelled(true);
        if (alreadyCancelled) return;
        if (!(event instanceof EntityDamageByEntityEvent damageByEntity)) return;
        if (!(damageByEntity.getDamager() instanceof Player player)) return;

        grave.leftClick(player);
    }
}
