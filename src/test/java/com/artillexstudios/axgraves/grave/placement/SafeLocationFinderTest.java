package com.artillexstudios.axgraves.grave.placement;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link SafeLocationFinder} against a fake in-memory voxel grid ({@link FakeBlockProbe})
 * instead of a live Bukkit world - this is exactly the seam {@link BlockProbe} exists for.
 */
class SafeLocationFinderTest {

    private static PlacementSettings settings(int minY, int maxY) {
        return new PlacementSettings(true, true, true, true, 125, 5, 16, minY, maxY, true);
    }

    @Test
    void safeDeathSpotIsNotRelocated() {
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SAFE);

        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, 64, 0, settings(-64, 319));

        assertFalse(result.relocated());
        assertEquals(0, result.x());
        assertEquals(64, result.y());
        assertEquals(0, result.z());
        assertEquals(1, result.probes());
    }

    @Test
    void lavaAtDeathSpotMovesUpToTheNextSafeBlock() {
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SAFE);
        probe.set(0, 64, 0, ProbeResult.HAZARD);

        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, 64, 0, settings(-64, 319));

        assertTrue(result.relocated());
        assertEquals(65, result.y());
    }

    @Test
    void voidDeathIsClampedToMinYAndNeedsNoFurtherSearchWhenThatSpotIsSafe() {
        // dying below the world's min Y is handled "for free" by the pre-clamp - this shows the
        // clamped spot getting accepted on the very first probe, with no relocation flagged
        // (LocationUtils.clampLocation already performs this exact clamp independently too).
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SAFE);

        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, -500, 0, settings(-64, 319));

        assertEquals(-64, result.y());
        assertFalse(result.relocated());
        assertEquals(1, result.probes());
    }

    @Test
    void buriedInStoneFindsTheNearestAirPocketAbove() {
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SOLID);
        probe.set(0, 70, 0, ProbeResult.SAFE);
        probe.set(0, 71, 0, ProbeResult.SAFE);

        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, 64, 0, settings(-64, 319));

        assertTrue(result.relocated());
        assertEquals(70, result.y());
    }

    @Test
    void netherRoofIsAvoidedEvenWhenTheBlocksThereAreOtherwiseSafe() {
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SAFE);
        PlacementSettings netherSettings = new PlacementSettings(true, true, true, true, 125, 0, 10, 0, 255, true);

        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, 130, 0, netherSettings);

        assertTrue(result.relocated());
        assertTrue(result.y() < 125, "expected a y below the nether roof, was " + result.y());
    }

    @Test
    void unloadedColumnsAreNeverSelectedOrProbedIntoLoading() {
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SOLID);
        // this would be the nearest candidate by vertical distance, but it's unloaded - the
        // search must skip straight past it rather than "finding" it or crashing.
        probe.set(0, 70, 0, ProbeResult.UNLOADED);
        probe.set(0, 71, 0, ProbeResult.UNLOADED);
        // the next genuinely safe pocket, further away.
        probe.set(0, 75, 0, ProbeResult.SAFE);
        probe.set(0, 76, 0, ProbeResult.SAFE);

        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, 64, 0, settings(-64, 319));

        assertTrue(result.relocated());
        assertEquals(75, result.y());
        assertTrue(probe.wasProbed(0, 70, 0), "the unloaded column should still have been consulted, just not selected");
    }

    @Test
    void searchNeverExceedsTheConfiguredProbeBudget() {
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SOLID); // nothing is ever safe
        int radius = 2;
        int verticalDistance = 2;
        PlacementSettings tight = new PlacementSettings(true, true, true, false, 125, radius, verticalDistance, -64, 319, true);

        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, 64, 0, tight);

        assertFalse(result.relocated());
        int perColumn = 1 + 2 * verticalDistance;
        int maxExpectedProbes = perColumn * (1 + 4 * radius * (radius + 1));
        assertTrue(result.probes() <= maxExpectedProbes,
                "expected at most " + maxExpectedProbes + " probes, got " + result.probes());
    }

    @Test
    void horizontalRadiusIsHardCappedRegardlessOfConfig() {
        PlacementSettings overshoot = new PlacementSettings(true, true, true, false, 125, 999, 0, -64, 319, true);
        assertEquals(PlacementSettings.HARD_RADIUS_CAP, overshoot.maxHorizontalRadius());
    }

    private static final class FakeBlockProbe implements BlockProbe {
        private final Map<Long, ProbeResult> overrides = new HashMap<>();
        private final Map<Long, Boolean> touched = new HashMap<>();
        private final ProbeResult fill;

        FakeBlockProbe(ProbeResult fill) {
            this.fill = fill;
        }

        void set(int x, int y, int z, ProbeResult result) {
            overrides.put(key(x, y, z), result);
        }

        boolean wasProbed(int x, int y, int z) {
            return touched.containsKey(key(x, y, z));
        }

        @Override
        public ProbeResult probe(int x, int y, int z) {
            long k = key(x, y, z);
            touched.put(k, Boolean.TRUE);
            return overrides.getOrDefault(k, fill);
        }

        private static long key(int x, int y, int z) {
            return (((long) x & 0x3FFFFFFL) << 38) | (((long) (y + 4096) & 0x1FFFL) << 26) | ((long) z & 0x3FFFFFFL);
        }
    }
}
