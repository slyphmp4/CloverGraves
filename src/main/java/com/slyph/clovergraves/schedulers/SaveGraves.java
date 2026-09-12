package com.slyph.clovergraves.schedulers;

import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.GraveSnapshot;
import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.storage.EndReason;
import com.slyph.clovergraves.storage.GraveRecord;
import com.slyph.clovergraves.storage.GraveStorage;
import com.slyph.clovergraves.utils.CloverLogger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.slyph.clovergraves.AxGraves.CONFIG;
import static com.slyph.clovergraves.AxGraves.EXECUTOR;

public class SaveGraves {
    private static ScheduledFuture<?> future;
    private static volatile int dataVersion = -1;

    public static void start() {
        if (future != null) future.cancel(false);
        dataVersion = resolveDataVersion();

        int seconds = CONFIG.getInt("storage.flush-interval-seconds", CONFIG.getInt("save-graves.auto-save-seconds", 30));
        if (seconds == -1) return;
        seconds = Math.max(1, seconds);

        future = EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                flushDirty();
            } catch (RuntimeException ex) {
                CloverLogger.error("failed to save graves", ex);
            }
        }, seconds, seconds, TimeUnit.SECONDS);
    }

    public static void stop() {
        if (future == null) return;
        future.cancel(false);
        future = null;
    }

    public static void saveNow(@NotNull Grave grave) {
        GraveStorage storage = SpawnedGraves.storage();
        if (storage == null) return;

        grave.contents().refreshSnapshot();
        GraveSnapshot snapshot = grave.snapshot();
        if (snapshot.version() == grave.lastPersistedVersion()) return;
        EXECUTOR.execute(() -> persistOne(grave, snapshot, storage));
    }

    public static void flushDirty() {
        GraveStorage storage = SpawnedGraves.storage();
        if (storage == null) return;

        flushRemovals(storage);

        List<PersistRequest> requests = new ArrayList<>();
        List<GraveRecord> records = new ArrayList<>();

        for (Grave grave : SpawnedGraves.getGraves()) {
            if (grave.isRemoved()) continue;
            GraveSnapshot snapshot = grave.snapshot();
            if (snapshot.version() == grave.lastPersistedVersion()) continue;

            requests.add(new PersistRequest(grave, snapshot));
            records.add(toRecord(grave, snapshot));
        }

        if (records.isEmpty()) return;

        try {
            List<Long> ids = storage.saveAll(records);
            if (ids.size() != requests.size()) {
                CloverLogger.error("storage returned {} ids for {} dirty graves; none were marked persisted", ids.size(), requests.size());
                return;
            }

            for (int i = 0; i < requests.size(); i++) {
                long assignedId = ids.get(i);
                PersistRequest request = requests.get(i);
                if (assignedId <= 0) {
                    continue;
                }

                request.grave().assignStorageId(assignedId);
                EndReason removedReason = SpawnedGraves.consumeUnsavedRemoval(request.grave());
                if (removedReason != null) {
                    removeOrRetry(storage, assignedId, removedReason);
                    continue;
                }
                request.grave().markPersisted(request.snapshot().version());
            }
        } catch (RuntimeException ex) {
            CloverLogger.error("failed to batch-save {} dirty grave(s)", records.size(), ex);
        }
    }

    public static void flushRemovals(@NotNull GraveStorage storage) {
        SpawnedGraves.PendingRemoval removal;
        while ((removal = SpawnedGraves.pollRemoval()) != null) {
            try {
                storage.remove(removal.storageId(), removal.reason());
            } catch (RuntimeException ex) {
                SpawnedGraves.retryRemoval(removal);
                CloverLogger.error("failed to remove grave {} from storage", removal.storageId(), ex);
                break; // Retry on the next flush, without spinning on an unavailable backend.
            }
        }
    }

    private static void persistOne(@NotNull Grave grave, @NotNull GraveSnapshot snapshot, @NotNull GraveStorage storage) {
        if (grave.isRemoved() || snapshot.version() <= grave.lastPersistedVersion()) return;
        try {
            long assignedId = storage.save(toRecord(grave, snapshot));
            if (assignedId <= 0) {
                return;
            }

            grave.assignStorageId(assignedId);
            EndReason removedReason = SpawnedGraves.consumeUnsavedRemoval(grave);
            if (removedReason != null) {
                removeOrRetry(storage, assignedId, removedReason);
                return;
            }
            grave.markPersisted(snapshot.version());
        } catch (RuntimeException ex) {
            CloverLogger.error("failed to save a grave to storage", ex);
        }
    }

    private static void removeOrRetry(GraveStorage storage, long id, EndReason reason) {
        try {
            storage.remove(id, reason);
        } catch (RuntimeException ex) {
            SpawnedGraves.retryRemoval(new SpawnedGraves.PendingRemoval(id, reason));
            CloverLogger.error("failed to remove grave {}; queued for retry", id, ex);
        }
    }

    @NotNull
    private static GraveRecord toRecord(@NotNull Grave grave, @NotNull GraveSnapshot snapshot) {
        return new GraveRecord(
                grave.storageId(),
                grave.getPlayer().getUniqueId(),
                grave.getPlayerName(),
                grave.getStorageLocation(),
                snapshot.serializedItems(),
                dataVersion,
                snapshot.storedXP(),
                grave.getSpawned(),
                null,
                null
        );
    }

    private static int resolveDataVersion() {
        try {
            return org.bukkit.Bukkit.getUnsafe().getDataVersion();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private record PersistRequest(@NotNull Grave grave, @NotNull GraveSnapshot snapshot) {
    }
}
