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
 *
 * <p>All scenarios run with {@code requireGroundSupport = true} (the shipped default): a spot is
 * only accepted if there is an actual solid block directly beneath it, not just "not solid/lava
 * itself". Without this, an empty column of void air is never solid or lava either, so a death
 * far below the world would pass the very first check and never be relocated at all - which is
 * exactly the bug this requirement exists to close.</p>
 */
class SafeLocationFinderTest {

    private static PlacementSettings settings(int minY, int maxY) {
        return new PlacementSettings(true, true, true, true, 125, false, true, 5, 16, minY, maxY, true);
    }

    @Test
    void safeDeathSpotOnSolidGroundIsNotRelocated() {
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SAFE);
        probe.set(0, 63, 0, ProbeResult.SOLID); // the ground the player is standing on

        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, 64, 0, settings(-64, 319));

        assertTrue(result.found());
        assertFalse(result.relocated());
        assertEquals(0, result.x());
        assertEquals(64, result.y());
        assertEquals(0, result.z());
        assertEquals(1, result.probes());
    }

    @Test
    void lavaAtDeathSpotMovesToTheNearestGroundedSafeSpot() {
        // solid rock everywhere by default; a lava pocket at the death column, and a genuine
        // 2-tall air pocket standing on solid ground one column over.
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SOLID);
        probe.set(0, 64, 0, ProbeResult.HAZARD);
        probe.set(1, 65, 0, ProbeResult.SAFE);
        probe.set(1, 66, 0, ProbeResult.SAFE);
        // (1, 64, 0) stays SOLID via the fill - that's the ground supporting (1, 65, 0)

        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, 64, 0, settings(-64, 319));

        assertTrue(result.found());
        assertTrue(result.relocated());
        assertEquals(1, result.x());
        assertEquals(65, result.y());
        assertEquals(0, result.z());
    }

    @Test
    void pureVoidWithNoGroundAnywhereIsNotFound() {
        // an entirely empty column (and everything around it, within budget) - nothing is ever
        // solid or lava, so without a ground-support requirement this would wrongly pass
        // instantly. This is what a real void death looks like; DeathListener's spawn-point
        // fallback (not exercised at this unit level) is what actually rescues it in-game.
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SAFE);

        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, -500, 0, settings(-64, 319));

        assertFalse(result.found());
        assertFalse(result.relocated());
    }

    @Test
    void buriedInStoneFindsTheNearestGroundedAirPocketAbove() {
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SOLID);
        probe.set(0, 70, 0, ProbeResult.SAFE);
        probe.set(0, 71, 0, ProbeResult.SAFE);
        // (0, 69, 0) stays SOLID via the fill - ground support for (0, 70, 0)

        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, 64, 0, settings(-64, 319));

        assertTrue(result.found());
        assertTrue(result.relocated());
        assertEquals(70, result.y());
    }

    @Test
    void netherRoofIsAvoidedEvenWhenGroundedSpaceThereIsOtherwiseSafe() {
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SOLID);
        probe.set(0, 122, 0, ProbeResult.SAFE);
        probe.set(0, 123, 0, ProbeResult.SAFE);
        // (0, 121, 0) stays SOLID via the fill - ground support for (0, 122, 0)

        PlacementSettings netherSettings = new PlacementSettings(true, true, true, true, 125, true, true, 0, 10, 0, 255, true);
        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, 130, 0, netherSettings);

        assertTrue(result.found());
        assertTrue(result.relocated());
        assertTrue(result.y() < 125, "expected a y below the nether roof, was " + result.y());
    }

    @Test
    void netherRoofDoesNotApplyOutsideTheNether() {
        // a mountain peak (or a tower, or an elytra death) well above y=125 in a NON-nether
        // world - the roof rule must not touch it. Before this was gated on isNetherWorld, this
        // safe overworld spot was force-relocated on every death above y=125, and once the search
        // range was widened elsewhere, "relocated" could mean 100+ blocks straight down.
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SAFE);
        probe.set(0, 199, 0, ProbeResult.SOLID);

        PlacementSettings overworldSettings = new PlacementSettings(true, true, true, true, 125, false, true, 5, 16, -64, 319, true);
        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, 200, 0, overworldSettings);

        assertTrue(result.found());
        assertFalse(result.relocated());
        assertEquals(200, result.y());
    }

    @Test
    void unloadedColumnsAreNeverSelectedOrProbedIntoLoading() {
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SOLID);
        // this would be the nearest candidate by vertical distance, but it's unloaded - the
        // search must skip straight past it rather than "finding" it or crashing.
        probe.set(0, 70, 0, ProbeResult.UNLOADED);
        probe.set(0, 71, 0, ProbeResult.UNLOADED);
        // the next genuinely safe, grounded pocket, further away.
        probe.set(0, 75, 0, ProbeResult.SAFE);
        probe.set(0, 76, 0, ProbeResult.SAFE);

        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, 64, 0, settings(-64, 319));

        assertTrue(result.found());
        assertTrue(result.relocated());
        assertEquals(75, result.y());
        assertTrue(probe.wasProbed(0, 70, 0), "the unloaded column should still have been consulted, just not selected");
    }

    @Test
    void searchNeverExceedsTheConfiguredProbeBudget() {
        FakeBlockProbe probe = new FakeBlockProbe(ProbeResult.SOLID); // feet are always solid: nothing is ever standable
        int radius = 2;
        int verticalDistance = 2;
        PlacementSettings tight = new PlacementSettings(true, true, true, false, 125, false, true, radius, verticalDistance, -64, 319, true);

        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, 0, 64, 0, tight);

        assertFalse(result.found());
        assertFalse(result.relocated());
        int perColumn = 1 + 2 * verticalDistance;
        int maxExpectedProbes = perColumn * (1 + 4 * radius * (radius + 1));
        assertTrue(result.probes() <= maxExpectedProbes,
                "expected at most " + maxExpectedProbes + " probes, got " + result.probes());
    }

    @Test
    void horizontalRadiusIsHardCappedRegardlessOfConfig() {
        PlacementSettings overshoot = new PlacementSettings(true, true, true, false, 125, false, true, 999, 0, -64, 319, true);
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
            // non-overlapping bit ranges: z[0..20], y[21..33], x[34..54] - the previous packing
            // shifted x by 38 while y's 13-bit field (shifted by 26) extended up to bit 38 too,
            // colliding for any (x, y) pair where x's low bit and y's top bit were both set.
            return (((long) x & 0x1FFFFFL) << 34) | (((long) (y + 4096) & 0x1FFFL) << 21) | ((long) z & 0x1FFFFFL);
        }
    }
}
