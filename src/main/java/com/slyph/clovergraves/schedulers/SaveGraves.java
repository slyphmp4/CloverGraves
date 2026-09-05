package com.slyph.clovergraves.schedulers;

import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.GraveSnapshot;
import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.storage.GraveRecord;
import com.slyph.clovergraves.storage.GraveStorage;
import com.slyph.clovergraves.storage.LocationCodec;
import com.slyph.clovergraves.utils.CloverLogger;
import org.jetbrains.annotations.NotNull;

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

        SpawnedGraves.PendingRemoval removal;
        while ((removal = SpawnedGraves.pollRemoval()) != null) {
            try {
                storage.remove(removal.storageId(), removal.reason());
            } catch (Exception ex) {
                CloverLogger.error("failed to remove grave {} from storage", removal.storageId(), ex);
            }
        }

        for (Grave grave : SpawnedGraves.getGraves()) {
            GraveSnapshot snapshot = grave.snapshot();
            if (snapshot.version() == grave.lastPersistedVersion()) continue;
            persistOne(grave, snapshot, storage);
        }
    }

    private static void persistOne(@NotNull Grave grave, @NotNull GraveSnapshot snapshot, @NotNull GraveStorage storage) {
        try {
            GraveRecord record = new GraveRecord(
                    grave.storageId(),
                    grave.getPlayer().getUniqueId(),
                    grave.getPlayerName(),
                    LocationCodec.serialize(grave.getLocation()),
                    snapshot.serializedItems(),
                    currentDataVersion(),
                    snapshot.storedXP(),
                    grave.getSpawned(),
                    null,
                    null
            );

            long assignedId = storage.save(record);
            grave.assignStorageId(assignedId);
            grave.markPersisted(snapshot.version());
        } catch (Exception ex) {
            CloverLogger.error("failed to save a grave to storage", ex);
        }
    }

    private static int currentDataVersion() {
        try {
            return org.bukkit.Bukkit.getUnsafe().getDataVersion();
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
