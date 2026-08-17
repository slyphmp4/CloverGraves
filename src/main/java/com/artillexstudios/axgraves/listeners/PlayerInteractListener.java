package com.artillexstudios.axgraves.listeners;

import com.artillexstudios.axapi.packet.wrapper.serverbound.ServerboundInteractWrapper;
import com.artillexstudios.axgraves.grave.BlockKey;
import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fallback for players who right-click the grave's block directly rather than its packet
 * entity. Previously this iterated every live grave and called {@code Location#getBlock()} on
 * <strong>every</strong> block click by <strong>any</strong> player - which force-loads the
 * chunk if it wasn't already loaded, was O(graves) with no early filtering, had no cancellation
 * guard, and ran for every action (including {@code PHYSICAL}, i.e. pressure plates). This does
 * an O(1) lookup keyed by block position and only runs for an actual right-click on an
 * already-loaded block.
 */
public class PlayerInteractListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (event.getHand() == null) return;

        ServerboundInteractWrapper.InteractionHand hand = switch (event.getHand()) {
            case HAND -> ServerboundInteractWrapper.InteractionHand.MAIN_HAND;
            case OFF_HAND -> ServerboundInteractWrapper.InteractionHand.OFF_HAND;
            default -> null;
        };
        if (hand == null) return;

        Location location = clicked.getLocation();
        Grave grave = SpawnedGraves.getGrave(new BlockKey(location.getWorld().getUID(), clicked.getX(), clicked.getY(), clicked.getZ()));
        if (grave == null) return;

        // stop the block underneath the grave (a chest, pressure plate, crop, ...) from also
        // reacting to the same click - the original listener never cancelled anything.
        event.setUseInteractedBlock(Event.Result.DENY);
        grave.interact(event.getPlayer(), hand);
    }
}
