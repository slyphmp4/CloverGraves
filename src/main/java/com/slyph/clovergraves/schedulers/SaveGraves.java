package com.slyph.clovergraves.schedulers;

import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.GraveSnapshot;
import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.storage.GraveRecord;
import com.slyph.clovergraves.storage.GraveStorage;
import com.slyph.clovergraves.storage.LocationCodec;
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

    public static void start() {
        if (future != null) future.cancel(false);

        int seconds = CONFIG.getInt("storage.flush-interval-seconds", CONFIG.getInt("save-graves.auto-save-seconds", 30));
        if (seconds == -1) return;
        seconds = Math.max(1, seconds);

        future = EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                flushDirty();
            } catch (Exception ex) {
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

        int dataVersion = currentDataVersion();
        List<PersistRequest> requests = new ArrayList<>();
        List<GraveRecord> records = new ArrayList<>();

        for (Grave grave : SpawnedGraves.getGraves()) {
            GraveSnapshot snapshot = grave.snapshot();
            if (snapshot.version() == grave.lastPersistedVersion()) continue;

            requests.add(new PersistRequest(grave, snapshot));
            records.add(toRecord(grave, snapshot, dataVersion));
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
                if (assignedId <= 0) continue;

                PersistRequest request = requests.get(i);
                request.grave().assignStorageId(assignedId);
                request.grave().markPersisted(request.snapshot().version());
            }
        } catch (Exception ex) {
            CloverLogger.error("failed to batch-save {} dirty grave(s)", records.size(), ex);
        }
    }

    private static void flushRemovals(@NotNull GraveStorage storage) {
        SpawnedGraves.PendingRemoval removal;
        while ((removal = SpawnedGraves.pollRemoval()) != null) {
            try {
                storage.remove(removal.storageId(), removal.reason());
            } catch (Exception ex) {
                CloverLogger.error("failed to remove grave {} from storage", removal.storageId(), ex);
            }
        }
    }

    private static void persistOne(@NotNull Grave grave, @NotNull GraveSnapshot snapshot, @NotNull GraveStorage storage) {
        try {
            long assignedId = storage.save(toRecord(grave, snapshot, currentDataVersion()));
            if (assignedId <= 0) return;

            grave.assignStorageId(assignedId);
            grave.markPersisted(snapshot.version());
        } catch (Exception ex) {
            CloverLogger.error("failed to save a grave to storage", ex);
        }
    }

    @NotNull
    private static GraveRecord toRecord(@NotNull Grave grave, @NotNull GraveSnapshot snapshot, int dataVersion) {
        return new GraveRecord(
                grave.storageId(),
                grave.getPlayer().getUniqueId(),
                grave.getPlayerName(),
                LocationCodec.serialize(grave.getLocation()),
                snapshot.serializedItems(),
                dataVersion,
                snapshot.storedXP(),
                grave.getSpawned(),
                null,
                null
        );
    }

    private static int currentDataVersion() {
        try {
            return org.bukkit.Bukkit.getUnsafe().getDataVersion();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private record PersistRequest(@NotNull Grave grave, @NotNull GraveSnapshot snapshot) {
    }
}
