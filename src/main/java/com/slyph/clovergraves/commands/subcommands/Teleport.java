package com.slyph.clovergraves.commands.subcommands;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.PaperUtils;
import com.artillexstudios.axapi.utils.StringUtils;
import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.utils.EconomyHook;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.slyph.clovergraves.AxGraves.CONFIG;
import static com.slyph.clovergraves.AxGraves.MESSAGEUTILS;

/**
 * {@code /axgraves tp} and {@code /bibingka grave tp} - both route here (see
 * {@code Commands}). Not instant: a per-second countdown runs first
 * ({@code teleport.warmup-seconds}, default 5), during which moving or taking damage cancels it
 * (see {@link com.slyph.clovergraves.listeners.TeleportCancelListener}). A successful
 * teleport starts a cooldown ({@code teleport.cooldown-seconds}, default 60) before the command
 * can be used again - a cancelled warmup does not consume it.
 *
 * <p>If {@code teleport.cost} is above 0, the first invocation only checks the player can afford
 * it and prompts them to run the command again within {@code teleport.confirmation-timeout-seconds}
 * to confirm - it does not charge or start the warmup yet. The actual charge happens at the very
 * end, right before the teleport itself, so a warmup that gets cancelled by moving/taking damage
 * never costs anything.</p>
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

        double cost = CONFIG.getDouble("teleport.cost", 0);
        String symbol = CONFIG.getString("teleport.currency-symbol", "$");

        if (cost > 0 && !TeleportConfirmations.consumeIfConfirmed(uuid)) {
            promptConfirmation(sender, uuid, cost, symbol);
            return;
        }

        startWarmup(sender, uuid, target, cost, symbol);
    }

    private void promptConfirmation(Player sender, UUID uuid, double cost, String symbol) {
        if (!EconomyHook.has(sender, cost)) {
            MESSAGEUTILS.sendLang(sender, "teleport.cost-insufficient", Map.of(
                    "%cost%", EconomyHook.format(cost, symbol),
                    "%balance%", EconomyHook.format(EconomyHook.balance(sender), symbol)));
            return;
        }

        int confirmSeconds = Math.max(CONFIG.getInt("teleport.confirmation-timeout-seconds", 15), 1);
        TeleportConfirmations.markConfirmable(uuid, confirmSeconds);
        MESSAGEUTILS.sendLang(sender, "teleport.cost-confirm", Map.of(
                "%cost%", EconomyHook.format(cost, symbol),
                "%seconds%", String.valueOf(confirmSeconds)));
    }

    private void startWarmup(Player sender, UUID uuid, Location target, double cost, String symbol) {
        int warmupSeconds = Math.max(CONFIG.getInt("teleport.warmup-seconds", 5), 0);
        if (warmupSeconds == 0) {
            completeTeleport(sender, uuid, target, cost, symbol);
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
                    completeTeleport(sender, uuid, target, cost, symbol);
                } else {
                    MESSAGEUTILS.sendLang(sender, "teleport.countdown", Map.of("%seconds%", String.valueOf(secondsLeft)));
                }
            }, () -> TeleportWarmups.clear(uuid), second * 20L);
        }
    }

    private void completeTeleport(Player sender, UUID uuid, Location target, double cost, String symbol) {
        if (cost > 0) {
            // re-checked here, not just at confirmation time - the player could have spent the
            // money elsewhere during the confirmation window or the warmup countdown.
            if (!EconomyHook.has(sender, cost) || !EconomyHook.withdraw(sender, cost)) {
                MESSAGEUTILS.sendLang(sender, "teleport.cost-insufficient", Map.of(
                        "%cost%", EconomyHook.format(cost, symbol),
                        "%balance%", EconomyHook.format(EconomyHook.balance(sender), symbol)));
                return;
            }
            MESSAGEUTILS.sendLang(sender, "teleport.cost-charged", Map.of("%cost%", EconomyHook.format(cost, symbol)));
        }

        TeleportWarmups.markUsed(uuid);
        MESSAGEUTILS.sendLang(sender, "teleport.warmup-complete");
        PaperUtils.teleportAsync(sender, target);
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
