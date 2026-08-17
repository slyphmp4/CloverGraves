package com.artillexstudios.axgraves.utils;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, Bukkit-free reordering of a pool of items so that a list of "priority" items comes
 * first (in priority order), followed by the remainder in their original relative order.
 * <p>
 * Matching is by {@link Object#equals(Object)} and each pool entry is consumed at most once,
 * even when several priority entries are equal to each other (e.g. two identical armor pieces) -
 * the previous implementation matched with {@code List#contains}/{@code List#remove} without
 * ever consuming, which was both a dead ("always false") guard and, with duplicate stacks,
 * capable of removing the wrong object from the pool.
 */
public final class ItemOrdering {

    private ItemOrdering() {
    }

    @NotNull
    public static <T> List<T> reorder(@NotNull List<T> pool, @NotNull List<T> priority) {
        List<T> remaining = new ArrayList<>(pool);
        List<T> ordered = new ArrayList<>(pool.size());

        for (T wanted : priority) {
            if (wanted == null) continue;
            int idx = remaining.indexOf(wanted);
            if (idx == -1) continue;
            ordered.add(remaining.remove(idx));
        }

        ordered.addAll(remaining);
        return ordered;
    }
}
