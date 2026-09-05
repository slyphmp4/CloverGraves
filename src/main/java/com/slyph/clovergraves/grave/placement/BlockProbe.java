package com.slyph.clovergraves.grave.placement;

/**
 * A single-block inspector. The seam that makes {@link SafeLocationFinder} testable without
 * Bukkit - tests supply a fake in-memory voxel grid, production supplies {@link BukkitBlockProbe}.
 */
@FunctionalInterface
public interface BlockProbe {
    ProbeResult probe(int x, int y, int z);
}
