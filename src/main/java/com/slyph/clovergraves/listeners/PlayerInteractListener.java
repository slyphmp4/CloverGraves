package com.slyph.clovergraves.listeners;

import com.slyph.clovergraves.config.GraveSettings;
import com.slyph.clovergraves.grave.BlockKey;
import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.SpawnedGraves;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerInteractListener implements Listener {
    private static final double TARGET_RADIUS = 1.15;
    private static final double TARGET_RADIUS_SQUARED = TARGET_RADIUS * TARGET_RADIUS;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            Grave grave = graveFromClickedBlock(event);
            if (grave == null) return;
            deny(event);
            return;
        }

        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        Grave grave = graveFromClickedBlock(event);
        if (grave == null) grave = findTargetedGrave(event.getPlayer());
        if (grave == null) return;

        boolean alreadyCancelled = event.isCancelled();
        deny(event);
        if (alreadyCancelled) return;

        grave.interact(event.getPlayer(), EquipmentSlot.HAND);
    }

    @Nullable
    private Grave graveFromClickedBlock(@NotNull PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) return null;

        Location location = clicked.getLocation();
        return SpawnedGraves.getGrave(new BlockKey(
                location.getWorld().getUID(),
                clicked.getX(),
                clicked.getY(),
                clicked.getZ()
        ));
    }

    @Nullable
    private Grave findTargetedGrave(@NotNull Player player) {
        double maxDistance = GraveSettings.current().interactRadius();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        Grave best = null;
        double bestProjection = Double.MAX_VALUE;

        for (Grave grave : SpawnedGraves.getGraves()) {
            if (grave.isRemoved()) continue;
            if (!player.getWorld().equals(grave.getLocation().getWorld())) continue;
            if (!player.hasLineOfSight(grave.getEntity())) continue;

            Location target = grave.getLocation().clone().add(0, 0.8, 0);
            Vector offset = target.toVector().subtract(eye.toVector());
            double projection = offset.dot(direction);
            if (projection < 0 || projection > maxDistance) continue;

            double perpendicularSquared = Math.max(0.0, offset.lengthSquared() - projection * projection);
            if (perpendicularSquared > TARGET_RADIUS_SQUARED) continue;
            if (projection >= bestProjection) continue;

            bestProjection = projection;
            best = grave;
        }

        return best;
    }

    private void deny(@NotNull PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }
}
