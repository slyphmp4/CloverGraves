package com.slyph.clovergraves.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.bukkit.Location;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationUtilsTest {

    @Test
    void centeringRequiresNoWorldOrBlockAccessAndPreservesRotation() {
        Location location = new Location(null, -0.2, 64.9, 2.1, 35, 20);
        Location centered = LocationUtils.getCenterOf(location, true, false);
        assertEquals(-0.5, centered.getX());
        assertEquals(64.5, centered.getY());
        assertEquals(2.5, centered.getZ());
        assertEquals(35, centered.getYaw());
        assertEquals(0, centered.getPitch());
        assertEquals(-0.2, location.getX());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "44, 0",
            "45, 90", // exactly halfway rounds up (Math.round semantics)
            "46, 90",
            "89, 90",
            "91, 90",
            "134, 90",
            "179, 180",
            "181, 180",
            "-44, 0",
            "-46, -90",
            "359, 360",
            "720, 720",
    })
    void snapsToNearestFortyFiveDegreeBoundary(float input, int expected) {
        assertEquals(expected, LocationUtils.getNearestDirection(input));
    }
}
