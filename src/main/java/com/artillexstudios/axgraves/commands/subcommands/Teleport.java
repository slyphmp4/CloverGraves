package com.artillexstudios.axgraves.commands.subcommands;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.PaperUtils;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;
import static com.artillexstudios.axgraves.AxGraves.MESSAGEUTILS;

/**
 * {@code /axgraves tp} and {@code /bibingka grave tp} - both route here (see
 * {@code Commands}). Not instant: a per-second countdown runs first
 * ({@code teleport.warmup-seconds}, default 5), during which moving or taking damage cancels it
 * (see {@link com.artillexstudios.axgraves.listeners.TeleportCancelListener}). A successful
 * teleport starts a cooldown ({@code teleport.cooldown-seconds}, default 60) before the command
 * can be used again - a cancelled warmup does not consume it.
 */
public enum Teleport {
    INSTANCE;

    public void execute(Player sender, World world, Double x, Double y, Double z) {
        Location target = resolveTarget(sender, world, x, y, z);
        if (target == null) return;

        UUID uuid = sender.getUniqueId();

        if (TeleportWarmups.isPending(uuid)) {
            MESSAGEUTILS.sendLang(sender, "teleport.already-pending");
            return;
        }

        int cooldownSeconds = CONFIG.getInt("teleport.cooldown-seconds", 60);
        long remaining = TeleportWarmups.remainingCooldownMillis(uuid, cooldownSeconds * 1_000L);
        if (remaining > 0) {
            MESSAGEUTILS.sendLang(sender, "teleport.cooldown", Map.of("%time%", StringUtils.formatTime(remaining)));
            return;
        }

        int warmupSeconds = Math.max(CONFIG.getInt("teleport.warmup-seconds", 5), 0);
        if (warmupSeconds == 0) {
            TeleportWarmups.markUsed(uuid);
            PaperUtils.teleportAsync(sender, target);
            return;
        }

        TeleportWarmups.startPending(uuid, sender.getLocation());
        MESSAGEUTILS.sendLang(sender, "teleport.warmup-start", Map.of("%seconds%", String.valueOf(warmupSeconds)));

        // one independent one-shot task per second, rather than a single repeating/self-
        // cancelling timer - avoids any race between scheduling a task and holding a reference
        // to cancel it, and each callback simply no-ops if TeleportWarmups no longer has this
        // player pending (i.e. the countdown was cancelled by movement/damage/quit).
        for (int second = 1; second <= warmupSeconds; second++) {
            boolean isLast = second == warmupSeconds;
            int secondsLeft = warmupSeconds - second;

            Scheduler.get().runLater(sender, task -> {
                if (!TeleportWarmups.isPending(uuid)) return;

                if (isLast) {
                    TeleportWarmups.clear(uuid);
                    TeleportWarmups.markUsed(uuid);
                    MESSAGEUTILS.sendLang(sender, "teleport.warmup-complete");
                    PaperUtils.teleportAsync(sender, target);
                } else {
                    MESSAGEUTILS.sendLang(sender, "teleport.countdown", Map.of("%seconds%", String.valueOf(secondsLeft)));
                }
            }, () -> TeleportWarmups.clear(uuid), second * 20L);
        }
    }

    @Nullable
    private Location resolveTarget(Player sender, World world, Double x, Double y, Double z) {
        if (world == null || x == null || y == null || z == null) {
            Grave grave = SpawnedGraves.getGraves().stream()
                    .filter(gr -> gr.getPlayer().getUniqueId().equals(sender.getUniqueId()))
                    .findAny().orElse(null);
            if (grave == null) {
                MESSAGEUTILS.sendLang(sender, "grave-list.no-graves");
                return null;
            }
            return grave.getLocation().clone().add(0, 0.5, 0);
        }

        final Location location = new Location(world, x, y, z);
        Optional<Grave> grave = SpawnedGraves.getGraves().stream()
                .filter(gr -> gr.getPlayer().getUniqueId().equals(sender.getUniqueId()))
                .filter(gr -> Objects.equals(gr.getLocation().getWorld(), location.getWorld()))
                .filter(gr -> gr.getLocation().distanceSquared(location) < 1)
                .findAny();

        if (!sender.hasPermission("axgraves.tp.bypass") && grave.isEmpty()) {
            MESSAGEUTILS.sendLang(sender, "commands.no-permission");
            return null;
        }

        return grave.map(value -> value.getLocation().clone().add(0, 0.5, 0)).orElse(location);
    }
}
