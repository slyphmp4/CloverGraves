package com.artillexstudios.axgraves.commands.subcommands;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks in-progress teleport warmups and per-player cooldowns for {@link Teleport}. Public so
 * {@link com.artillexstudios.axgraves.listeners.TeleportCancelListener} (a different package)
 * can cancel a pending warmup when the player moves, takes damage, or disconnects.
 */
public final class TeleportWarmups {
    private TeleportWarmups() {
    }

    public record Pending(@NotNull Location origin) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_USE = new ConcurrentHashMap<>();

    public static void startPending(@NotNull UUID uuid, @NotNull Location origin) {
        PENDING.put(uuid, new Pending(origin));
    }

    @Nullable
    public static Pending get(@NotNull UUID uuid) {
        return PENDING.get(uuid);
    }

    public static boolean isPending(@NotNull UUID uuid) {
        return PENDING.containsKey(uuid);
    }

    /** @return true if a pending warmup was actually present and removed */
    public static boolean clear(@NotNull UUID uuid) {
        return PENDING.remove(uuid) != null;
    }

    public static long remainingCooldownMillis(@NotNull UUID uuid, long cooldownMillis) {
        Long last = LAST_USE.get(uuid);
        if (last == null) return 0;
        return Math.max(0, cooldownMillis - (System.currentTimeMillis() - last));
    }

    public static void markUsed(@NotNull UUID uuid) {
        LAST_USE.put(uuid, System.currentTimeMillis());
    }
}
