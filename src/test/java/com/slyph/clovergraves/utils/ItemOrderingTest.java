package com.slyph.clovergraves.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemOrderingTest {

    @Test
    void priorityItemsComeFirstInPriorityOrder() {
        List<String> pool = List.of("sword", "helmet", "boots", "dirt");
        List<String> priority = List.of("boots", "helmet");

        assertEquals(List.of("boots", "helmet", "sword", "dirt"), ItemOrdering.reorder(pool, priority));
    }

    @Test
    void missingPriorityItemsAreIgnored() {
        List<String> pool = List.of("sword", "dirt");
        List<String> priority = List.of("boots", "helmet");

        assertEquals(List.of("sword", "dirt"), ItemOrdering.reorder(pool, priority));
    }

    @Test
    void emptyPriorityListReturnsPoolUnchanged() {
        List<String> pool = List.of("a", "b", "c");
        assertEquals(pool, ItemOrdering.reorder(pool, List.of()));
    }

    @Test
    void duplicatePoolEntriesAreConsumedOncePerPriorityMatch() {
        // two identical "helmet" entries in the pool: only one should be pulled to the front
        // per matching priority entry, and the other must survive in the remainder - the
        // original implementation's dead `contains` guard could drop the wrong instance here.
        List<String> pool = List.of("helmet", "helmet", "sword");
        List<String> priority = List.of("helmet");

        List<String> result = ItemOrdering.reorder(pool, priority);
        assertEquals(List.of("helmet", "helmet", "sword"), result);
        assertEquals(3, result.size());
    }

    @Test
    void priorityListWithDuplicatesPullsMultipleMatchingPoolEntries() {
        List<String> pool = List.of("sword", "helmet", "helmet");
        List<String> priority = List.of("helmet", "helmet");

        assertEquals(List.of("helmet", "helmet", "sword"), ItemOrdering.reorder(pool, priority));
    }

    @Test
    void nullPriorityEntriesAreSkipped() {
        List<String> pool = List.of("sword", "dirt");
        List<String> priority = new java.util.ArrayList<>();
        priority.add(null);
        priority.add("dirt");

        assertEquals(List.of("dirt", "sword"), ItemOrdering.reorder(pool, priority));
    }
}
