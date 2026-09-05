package com.slyph.clovergraves.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the pure {@code UpdateNotifier.isOutdated(String, String)} split out of the
 * instance method - the original threw {@code NumberFormatException}/
 * {@code ArrayIndexOutOfBoundsException} straight out of an async repeating timer for any
 * malformed response body (an HTML error page, a short/garbage version string, ...).
 */
class UpdateNotifierTest {

    @Test
    void newerLatestIsOutdated() {
        assertTrue(UpdateNotifier.isOutdated("1.30.0", "1.29.0"));
    }

    @Test
    void olderLatestIsNotOutdated() {
        assertFalse(UpdateNotifier.isOutdated("1.28.0", "1.29.0"));
    }

    @Test
    void equalVersionsAreNotOutdated() {
        assertFalse(UpdateNotifier.isOutdated("1.29.0", "1.29.0"));
    }

    @Test
    void shortVersionStringNeverThrows() {
        assertDoesNotThrow(() -> UpdateNotifier.isOutdated("1.2", "1.29.0"));
        assertFalse(UpdateNotifier.isOutdated("1.2", "1.29.0"));
    }

    @Test
    void garbageResponseBodyNeverThrows() {
        assertDoesNotThrow(() -> UpdateNotifier.isOutdated("<html>error</html>", "1.29.0"));
        assertFalse(UpdateNotifier.isOutdated("<html>error</html>", "1.29.0"));
    }

    @Test
    void emptyOrNullNeverThrow() {
        assertDoesNotThrow(() -> UpdateNotifier.isOutdated("", "1.29.0"));
        assertDoesNotThrow(() -> UpdateNotifier.isOutdated(null, "1.29.0"));
        assertFalse(UpdateNotifier.isOutdated("", "1.29.0"));
    }

    @Test
    void patchVersionDifferenceIsDetected() {
        assertTrue(UpdateNotifier.isOutdated("1.29.1", "1.29.0"));
    }

    @Test
    void nonNumericPatchComponentNeverThrowsAndIsNotOutdated() {
        // e.g. a "-SNAPSHOT"-suffixed build string in the third component
        assertDoesNotThrow(() -> UpdateNotifier.isOutdated("1.29.1-SNAPSHOT", "1.29.0"));
        assertFalse(UpdateNotifier.isOutdated("1.29.1-SNAPSHOT", "1.29.0"));
    }
}
