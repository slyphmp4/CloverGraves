package com.slyph.clovergraves.grave;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public record ChunkKey(@NotNull UUID worldId, int x, int z) {
    @NotNull
    public static ChunkKey of(@NotNull Location location) {
        World world = Objects.requireNonNull(location.getWorld(), "location world");
        return new ChunkKey(world.getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @NotNull
    public static ChunkKey of(@NotNull Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
}
