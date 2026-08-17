package com.artillexstudios.axgraves.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "-5, 1",
            "0, 1",
            "1, 1",
            "9, 1",
            "10, 2",
            "54, 6",
            "55, 6", // clamp: previously unbounded, would have made Bukkit.createInventory throw
            "200, 6",
    })
    void requiredRowsIsClampedToSix(int amount, int expectedRows) {
        assertEquals(expectedRows, InventoryUtils.getRequiredRows(amount));
    }

    @org.junit.jupiter.api.Test
    void maxSlotsMatchesSixRowsOfNine() {
        assertEquals(54, InventoryUtils.MAX_SLOTS);
        assertTrue(InventoryUtils.getRequiredRows(InventoryUtils.MAX_SLOTS) <= InventoryUtils.MAX_ROWS);
    }
}
