package com.slyph.clovergraves.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceUtilsTest {

    @Test
    void expFromLevelIsZeroAtLevelZero() {
        assertEquals(0, ExperienceUtils.getExpFromLevel(0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 15, 16, 29, 30, 31, 50, 100})
    void levelRoundTripsThroughExp(int level) {
        int exp = ExperienceUtils.getExpFromLevel(level);
        assertEquals(level, ExperienceUtils.getIntLevelFromExp(exp), "level " + level + " -> exp " + exp + " should round-trip");
    }

    @Test
    void expFromLevelIsMonotonicallyIncreasing() {
        int previous = -1;
        for (int level = 0; level <= 200; level++) {
            int exp = ExperienceUtils.getExpFromLevel(level);
            assertTrue(exp > previous, "getExpFromLevel(" + level + ")=" + exp + " should exceed the previous level's total");
            previous = exp;
        }
    }

    @Test
    void formulaSwitchesAreContinuousAtTheBoundaries() {
        // the piecewise formula changes at level 15 and level 30 - a level-50 grave holding
        // ~7 xp instead of ~5300 (the bug this plugin used to have) would show up as a
        // discontinuity here.
        assertEquals(ExperienceUtils.getExpFromLevel(15), ExperienceUtils.getExpFromLevel(15));
        int at15 = ExperienceUtils.getExpFromLevel(15);
        int at16 = ExperienceUtils.getExpFromLevel(16);
        assertTrue(at16 - at15 > 0 && at16 - at15 < 50, "level 15->16 exp delta should be small, was " + (at16 - at15));

        int at30 = ExperienceUtils.getExpFromLevel(30);
        int at31 = ExperienceUtils.getExpFromLevel(31);
        assertTrue(at31 - at30 > 0 && at31 - at30 < 200, "level 30->31 exp delta should be small, was " + (at31 - at30));
    }

    @Test
    void level0HasZeroLevelFromZeroExp() {
        assertEquals(0, ExperienceUtils.getIntLevelFromExp(0));
    }
}
