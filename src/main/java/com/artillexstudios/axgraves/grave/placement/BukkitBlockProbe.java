package com.artillexstudios.axgraves.grave.placement;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * Reads real world blocks for {@link SafeLocationFinder}. Reports {@link ProbeResult#UNLOADED}
 * instead of touching the block for any chunk that is not already loaded, so the search can
 * never force-load or force-generate a chunk during a death-time scan.
 */
public final class BukkitBlockProbe implements BlockProbe {
    private final World world;

    public BukkitBlockProbe(@NotNull World world) {
        this.world = world;
    }

    @Override
    public ProbeResult probe(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return ProbeResult.OUT_OF_BOUNDS;

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) return ProbeResult.UNLOADED;

        Block block = world.getBlockAt(x, y, z);
        Material type = block.getType();

        if (type == Material.LAVA) return ProbeResult.HAZARD;
        if (type.isSolid()) return ProbeResult.SOLID;

        return ProbeResult.SAFE;
    }
}
