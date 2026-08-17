package com.artillexstudios.axgraves.grave.placement;

import org.jetbrains.annotations.NotNull;

/**
 * Bukkit-free search for a "safe enough" block near a death location: not the void, not lava,
 * not embedded in solid blocks, not above the nether roof. Pure with respect to {@link BlockProbe}
 * so it can be exercised against a fake voxel grid in tests without touching Bukkit.
 *
 * <p>Never expands into a column the probe reports {@link ProbeResult#UNLOADED} for - the probe
 * is expected to report that instead of loading the chunk, so this search can never force a
 * chunk load, and it never probes more than a bounded number of columns
 * (see {@link PlacementSettings#HARD_RADIUS_CAP}).</p>
 */
public final class SafeLocationFinder {

    private SafeLocationFinder() {
    }

    public record Result(int x, int y, int z, boolean relocated, int probes) {
    }

    @NotNull
    public static Result find(@NotNull BlockProbe probe, int x, int y, int z, @NotNull PlacementSettings settings) {
        int minY = settings.minY();
        int maxY = settings.maxY();
        // clamping here is what makes "avoid the void" free: the death spot can never even be
        // probed below minY/above maxY in the first place.
        int startY = Math.clamp(y, minY, maxY);
        int probes = 0;

        probes++;
        if (isSafeColumnSpot(probe, x, startY, z, settings)) {
            return new Result(x, startY, z, false, probes);
        }

        // vertical scan at the same x/z first - closest to where the player actually died.
        for (int dy = 1; dy <= settings.maxVerticalDistance(); dy++) {
            int up = startY + dy;
            if (up <= maxY) {
                probes++;
                if (isSafeColumnSpot(probe, x, up, z, settings)) {
                    return new Result(x, up, z, true, probes);
                }
            }
            int down = startY - dy;
            if (down >= minY) {
                probes++;
                if (isSafeColumnSpot(probe, x, down, z, settings)) {
                    return new Result(x, down, z, true, probes);
                }
            }
        }

        // bounded horizontal rings outward from (x,z), each re-running the same vertical scan.
        int radius = settings.maxHorizontalRadius();
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // ring boundary only

                    int px = x + dx;
                    int pz = z + dz;

                    for (int dy = 0; dy <= settings.maxVerticalDistance(); dy++) {
                        int up = startY + dy;
                        if (up <= maxY) {
                            probes++;
                            if (isSafeColumnSpot(probe, px, up, pz, settings)) {
                                return new Result(px, up, pz, true, probes);
                            }
                        }
                        if (dy > 0) {
                            int down = startY - dy;
                            if (down >= minY) {
                                probes++;
                                if (isSafeColumnSpot(probe, px, down, pz, settings)) {
                                    return new Result(px, down, pz, true, probes);
                                }
                            }
                        }
                    }
                }
            }
        }

        // nothing better within budget - fall back to the clamped original spot rather than
        // failing outright.
        return new Result(x, startY, z, false, probes);
    }

    private static boolean isSafeColumnSpot(@NotNull BlockProbe probe, int x, int y, int z, @NotNull PlacementSettings settings) {
        if (settings.avoidNetherRoof() && y >= settings.netherRoofY()) return false;

        ProbeResult feet = probe.probe(x, y, z);
        if (!acceptable(feet, settings)) return false;

        ProbeResult above = probe.probe(x, y + 1, z);
        return acceptable(above, settings);
    }

    private static boolean acceptable(@NotNull ProbeResult result, @NotNull PlacementSettings settings) {
        return switch (result) {
            case UNLOADED, OUT_OF_BOUNDS -> false;
            case SOLID -> !settings.avoidSolid();
            case HAZARD -> !settings.avoidLava();
            case SAFE -> true;
        };
    }
}
