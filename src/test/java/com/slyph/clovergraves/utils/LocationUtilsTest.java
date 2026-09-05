package com.slyph.clovergraves.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationUtilsTest {

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
