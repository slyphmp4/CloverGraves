package com.slyph.clovergraves.storage;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists live grave state and (for backends that support it) a history of past graves. All
 * methods may block on IO and must only ever be called off the main/region thread.
 */
public interface GraveStorage {

    void init();

    @NotNull
    List<GraveRecord> loadAll();

    /** Upserts a live grave's current state; returns the record's id (newly assigned if {@code record.id() <= 0}). */
    long save(@NotNull GraveRecord record);

    /**
     * Saves multiple live grave records. Implementations may override this to reuse a connection,
     * transaction or database batch. The returned ids are aligned with the input order.
     */
    @NotNull
    default List<Long> saveAll(@NotNull List<GraveRecord> records) {
        List<Long> ids = new ArrayList<>(records.size());
        for (GraveRecord record : records) ids.add(save(record));
        return ids;
    }

    /** Removes a live grave and, if the backend supports history, archives it under {@code reason}. */
    void remove(long id, @NotNull EndReason reason);

    @NotNull
    List<GraveRecord> history(@NotNull UUID owner, int limit);

    /** A single history entry by id, used to reconstruct the grave being restored. */
    @NotNull
    Optional<GraveRecord> historyEntry(long historyId);

    /**
     * Atomically marks a history entry as restored, returning {@code true} the first time and
     * {@code false} on every later call for the same id - the guard against restoring the same
     * grave twice. Backends without history support (see {@link #history}) always return false.
     */
    boolean claimForRestore(long historyId);

    void close();
}
