package com.artillexstudios.axgraves.listeners;

import com.artillexstudios.axgraves.commands.subcommands.TeleportWarmups;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;
import static com.artillexstudios.axgraves.AxGraves.MESSAGEUTILS;

/** Cancels a pending {@code /axgraves tp} warmup on movement, damage, or disconnect. */
public class TeleportCancelListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onMove(@NotNull PlayerMoveEvent event) {
        if (!CONFIG.getBoolean("teleport.cancel-on-move", true)) return;

        Player player = event.getPlayer();
        TeleportWarmups.Pending pending = TeleportWarmups.get(player.getUniqueId());
        if (pending == null) return;

        Location to = event.getTo();
        if (to == null) return;

        Location from = pending.origin();
        boolean moved = !java.util.Objects.equals(from.getWorld(), to.getWorld()) || from.distanceSquared(to) > 0.04; // ~0.2 blocks, ignores pure head-turns
        if (moved) cancel(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(@NotNull EntityDamageEvent event) {
        if (!CONFIG.getBoolean("teleport.cancel-on-damage", true)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (TeleportWarmups.get(player.getUniqueId()) == null) return;

        cancel(player);
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        TeleportWarmups.clear(event.getPlayer().getUniqueId());
    }

    private void cancel(@NotNull Player player) {
        if (TeleportWarmups.clear(player.getUniqueId())) {
            MESSAGEUTILS.sendLang(player, "teleport.cancelled");
        }
    }
}
