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
    private static final double HITBOX_Y_OFFSET = -0.25;
    private static final double BLOCK_HIT_EPSILON = 0.05;
    private static final double DIRECTION_EPSILON = 1.0E-9;

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
        if (grave == null) grave = findTargetedGrave(event.getPlayer(), event.getClickedBlock());
        if (grave == null) return;

        deny(event);
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
    private Grave findTargetedGrave(@NotNull Player player, @Nullable Block clickedBlock) {
        GraveSettings settings = GraveSettings.current();
        double maxDistance = settings.interactRadius();
        Location eye = player.getEyeLocation();
        Vector origin = eye.toVector();
        Vector direction = eye.getDirection().normalize();

        if (clickedBlock != null) {
            double blockDistance = rayBoxDistance(
                    origin,
                    direction,
                    clickedBlock.getX(),
                    clickedBlock.getY(),
                    clickedBlock.getZ(),
                    clickedBlock.getX() + 1.0,
                    clickedBlock.getY() + 1.0,
                    clickedBlock.getZ() + 1.0,
                    maxDistance
            );
            if (blockDistance >= 0) {
                maxDistance = Math.min(maxDistance, blockDistance + BLOCK_HIT_EPSILON);
            }
        }

        Grave best = null;
        double bestDistance = Double.MAX_VALUE;
        double halfWidth = settings.interactionHitboxWidth() / 2.0;
        double height = settings.interactionHitboxHeight();

        for (Grave grave : SpawnedGraves.getGraves()) {
            if (grave.isRemoved()) continue;
            Location graveLocation = grave.getLocation();
            if (!player.getWorld().equals(graveLocation.getWorld())) continue;

            double minY = graveLocation.getY() + HITBOX_Y_OFFSET;
            double distance = rayBoxDistance(
                    origin,
                    direction,
                    graveLocation.getX() - halfWidth,
                    minY,
                    graveLocation.getZ() - halfWidth,
                    graveLocation.getX() + halfWidth,
                    minY + height,
                    graveLocation.getZ() + halfWidth,
                    maxDistance
            );
            if (distance < 0 || distance >= bestDistance) continue;

            bestDistance = distance;
            best = grave;
        }

        return best;
    }

    private double rayBoxDistance(@NotNull Vector origin, @NotNull Vector direction,
                                  double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ,
                                  double maxDistance) {
        double[] range = {0.0, maxDistance};
        if (!clipAxis(origin.getX(), direction.getX(), minX, maxX, range)) return -1;
        if (!clipAxis(origin.getY(), direction.getY(), minY, maxY, range)) return -1;
        if (!clipAxis(origin.getZ(), direction.getZ(), minZ, maxZ, range)) return -1;
        return range[0] <= maxDistance ? range[0] : -1;
    }

    private boolean clipAxis(double origin, double direction, double min, double max, double[] range) {
        if (Math.abs(direction) < DIRECTION_EPSILON) {
            return origin >= min && origin <= max;
        }

        double first = (min - origin) / direction;
        double second = (max - origin) / direction;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }

        range[0] = Math.max(range[0], first);
        range[1] = Math.min(range[1], second);
        return range[1] >= range[0];
    }

    private void deny(@NotNull PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }
}
