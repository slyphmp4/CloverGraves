package com.slyph.clovergraves.commands.subcommands;

import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.schedulers.CloverScheduler;
import com.slyph.clovergraves.utils.EconomyHook;
import com.slyph.clovergraves.utils.TextFormatter;
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
            MESSAGEUTILS.sendLang(sender, "teleport.cooldown", Map.of("%time%", TextFormatter.formatTime(remaining)));
            return;
        }

        double cost = Math.max(0, CONFIG.getDouble("teleport.cost", 0));
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
                    "%balance%", EconomyHook.format(EconomyHook.balance(sender), symbol)
            ));
            return;
        }

        int confirmSeconds = Math.max(CONFIG.getInt("teleport.confirmation-timeout-seconds", 15), 1);
        TeleportConfirmations.markConfirmable(uuid, confirmSeconds);
        MESSAGEUTILS.sendLang(sender, "teleport.cost-confirm", Map.of(
                "%cost%", EconomyHook.format(cost, symbol),
                "%seconds%", String.valueOf(confirmSeconds)
        ));
    }

    private void startWarmup(Player sender, UUID uuid, Location target, double cost, String symbol) {
        int warmupSeconds = Math.max(CONFIG.getInt("teleport.warmup-seconds", 5), 0);
        if (warmupSeconds == 0) {
            completeTeleport(sender, uuid, target, cost, symbol);
            return;
        }

        TeleportWarmups.startPending(uuid, sender.getLocation());
        MESSAGEUTILS.sendLang(sender, "teleport.warmup-start", Map.of("%seconds%", String.valueOf(warmupSeconds)));

        for (int second = 1; second <= warmupSeconds; second++) {
            boolean last = second == warmupSeconds;
            int secondsLeft = warmupSeconds - second;
            CloverScheduler.get().runLater(sender, task -> {
                if (!TeleportWarmups.isPending(uuid)) return;
                if (last) {
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
            if (!EconomyHook.has(sender, cost) || !EconomyHook.withdraw(sender, cost)) {
                MESSAGEUTILS.sendLang(sender, "teleport.cost-insufficient", Map.of(
                        "%cost%", EconomyHook.format(cost, symbol),
                        "%balance%", EconomyHook.format(EconomyHook.balance(sender), symbol)
                ));
                return;
            }
            MESSAGEUTILS.sendLang(sender, "teleport.cost-charged", Map.of("%cost%", EconomyHook.format(cost, symbol)));
        }

        if (!sender.teleport(target)) {
            MESSAGEUTILS.sendLang(sender, "teleport.cancelled");
            return;
        }

        TeleportWarmups.markUsed(uuid);
        MESSAGEUTILS.sendLang(sender, "teleport.warmup-complete");
    }

    @Nullable
    private Location resolveTarget(Player sender, World world, Double x, Double y, Double z) {
        if (world == null || x == null || y == null || z == null) {
            Grave grave = SpawnedGraves.getGraves().stream()
                    .filter(value -> value.getPlayer().getUniqueId().equals(sender.getUniqueId()))
                    .min(java.util.Comparator.comparingLong(Grave::getSpawned).reversed())
                    .orElse(null);
            if (grave == null) {
                MESSAGEUTILS.sendLang(sender, "grave-list.no-graves");
                return null;
            }
            return grave.getLocation().clone().add(0, 0.5, 0);
        }

        Location requested = new Location(world, x, y, z);
        Optional<Grave> grave = SpawnedGraves.getGraves().stream()
                .filter(value -> value.getPlayer().getUniqueId().equals(sender.getUniqueId()))
                .filter(value -> Objects.equals(value.getLocation().getWorld(), requested.getWorld()))
                .filter(value -> value.getLocation().distanceSquared(requested) < 1)
                .findFirst();

        if (!sender.hasPermission("axgraves.tp.bypass") && grave.isEmpty()) {
            MESSAGEUTILS.sendLang(sender, "commands.no-permission");
            return null;
        }

        return grave.map(value -> value.getLocation().clone().add(0, 0.5, 0)).orElse(requested);
    }
}
