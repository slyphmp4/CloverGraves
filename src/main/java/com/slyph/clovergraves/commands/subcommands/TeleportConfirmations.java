package com.slyph.clovergraves.commands.subcommands;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the short-lived "yes, I want to pay {@code teleport.cost} for this" confirmation for
 * {@link Teleport} - a player confirms by simply running the same teleport command again within
 * {@code teleport.confirmation-timeout-seconds}, which consumes the flag and lets the second call
 * proceed straight to the warmup instead of prompting again.
 */
final class TeleportConfirmations {
    private TeleportConfirmations() {
    }

    private static final Map<UUID, Long> CONFIRMED_UNTIL = new ConcurrentHashMap<>();

    static void markConfirmable(@NotNull UUID uuid, long windowSeconds) {
        CONFIRMED_UNTIL.put(uuid, System.currentTimeMillis() + windowSeconds * 1_000L);
    }

    /** @return true once, only if a confirmation is pending and hasn't expired - a second call returns false. */
    static boolean consumeIfConfirmed(@NotNull UUID uuid) {
        Long expiry = CONFIRMED_UNTIL.remove(uuid);
        return expiry != null && System.currentTimeMillis() <= expiry;
    }
}
