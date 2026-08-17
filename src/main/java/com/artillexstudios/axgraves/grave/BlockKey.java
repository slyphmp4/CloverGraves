package com.artillexstudios.axgraves.grave;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Identifies the block a grave occupies. Used to index live graves for O(1) lookup from
 * {@link com.artillexstudios.axgraves.listeners.PlayerInteractListener}, replacing the previous
 * "scan every grave and call {@code Location#getBlock()} to compare" approach, which force-loads
 * chunks and is O(n) per click.
 */
public record BlockKey(@NotNull UUID world, int x, int y, int z) {

    @NotNull
    public static BlockKey of(@NotNull Location location) {
        if (location.getWorld() == null) throw new IllegalArgumentException("location has no world");
        return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @NotNull
    public static BlockKey of(@NotNull Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
