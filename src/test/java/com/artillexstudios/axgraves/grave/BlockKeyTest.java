package com.artillexstudios.axgraves.grave;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BlockKeyTest {

    private static final UUID WORLD_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORLD_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void sameCoordinatesInSameWorldAreEqual() {
        assertEquals(new BlockKey(WORLD_A, 1, 2, 3), new BlockKey(WORLD_A, 1, 2, 3));
        assertEquals(new BlockKey(WORLD_A, 1, 2, 3).hashCode(), new BlockKey(WORLD_A, 1, 2, 3).hashCode());
    }

    @Test
    void sameCoordinatesInDifferentWorldsAreNotEqual() {
        assertNotEquals(new BlockKey(WORLD_A, 1, 2, 3), new BlockKey(WORLD_B, 1, 2, 3));
    }

    @Test
    void negativeCoordinatesAreDistinctFromPositive() {
        assertNotEquals(new BlockKey(WORLD_A, -1, 2, 3), new BlockKey(WORLD_A, 1, 2, 3));
        assertEquals(new BlockKey(WORLD_A, -5, -10, -15), new BlockKey(WORLD_A, -5, -10, -15));
    }
}
