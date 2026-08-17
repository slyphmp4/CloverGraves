package com.artillexstudios.axgraves.utils;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A permission plugin granting {@code axgraves.limit.*} (a normal wildcard grant) or a
 * malformed child node used to throw {@code NumberFormatException} straight out of the death
 * path via the previous unguarded {@code Integer.parseInt}.
 */
class LimitUtilsTest {

    @Test
    void parsesAValidLimitNode() {
        assertEquals(OptionalInt.of(5), LimitUtils.parseLimitNode("axgraves.limit.5"));
    }

    @Test
    void wildcardNodeNeverThrowsAndIsIgnored() {
        assertDoesNotThrow(() -> LimitUtils.parseLimitNode("axgraves.limit.*"));
        assertTrue(LimitUtils.parseLimitNode("axgraves.limit.*").isEmpty());
    }

    @Test
    void emptySuffixNeverThrows() {
        assertTrue(LimitUtils.parseLimitNode("axgraves.limit.").isEmpty());
    }

    @Test
    void nonNumericChildNodeNeverThrows() {
        assertTrue(LimitUtils.parseLimitNode("axgraves.limit.5.foo").isEmpty());
    }

    @Test
    void tooLargeToParseNeverThrows() {
        assertTrue(LimitUtils.parseLimitNode("axgraves.limit.99999999999999999999").isEmpty());
    }

    @Test
    void unrelatedPermissionIsIgnored() {
        assertTrue(LimitUtils.parseLimitNode("axgraves.admin").isEmpty());
    }
}
