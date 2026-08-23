package com.artillexstudios.axgraves.schedulers;

import com.artillexstudios.axapi.serializers.Serializers;
import com.artillexstudios.axapi.utils.logging.LogUtils;
import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.GraveSnapshot;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import com.artillexstudios.axgraves.storage.GraveRecord;
import com.artillexstudios.axgraves.storage.GraveStorage;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;
import static com.artillexstudios.axgraves.AxGraves.EXECUTOR;

/**
 * Periodically persists live graves via {@link GraveStorage}. Only ever reads
 * {@link Grave#snapshot()} - an immutable, pre-serialized {@link GraveSnapshot} that each grave's
 * own region-owned {@link Grave#tick()} keeps fresh - so this runs entirely off-thread without
 * ever touching a live Bukkit {@code Inventory} or NMS {@code ItemStack}. The original
 * implementation did the NMS serialization itself, from this same off-thread executor, which
 * raced whatever region/main thread happened to be looting the grave concurrently.
 */
public class SaveGraves {
    private static ScheduledFuture<?> future = null;

    public static void start() {
        if (future != null) future.cancel(true);

        int seconds = CONFIG.getInt("storage.flush-interval-seconds", CONFIG.getInt("save-graves.auto-save-seconds", 30));
        if (seconds == -1) return;

        future = EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                flushDirty();
            } catch (Exception ex) {
                LogUtils.error("failed to save graves", ex);
            }
        }, seconds, seconds, TimeUnit.SECONDS);
    }

    public static void stop() {
        if (future == null) return;
        future.cancel(true);
    }

    /**
     * Persists a single, just-created grave immediately, off-thread. Without this, a brand new
     * grave is only protected by the next scheduled {@link #flushDirty()} run - up to
     * {@code storage.flush-interval-seconds} away (15s by default) - and its
     * {@link GraveSnapshot} isn't even populated with real data until its own first
     * {@link Grave#tick()} fires 100ms after construction. A hard crash or kill anywhere in that
     * window loses the grave, and everything in it, with no trace in storage at all. Call this
     * from the grave's owning region right after it's added to {@link SpawnedGraves}.
     */
    public static void saveNow(@NotNull Grave grave) {
        GraveStorage storage = SpawnedGraves.storage();
        if (storage == null) return;

        grave.contents().refreshSnapshot();
        GraveSnapshot snap = grave.snapshot();
        if (snap.version() == grave.lastPersistedVersion()) return;

        EXECUTOR.execute(() -> persistOne(grave, snap, storage));
    }

    public static void flushDirty() {
        GraveStorage storage = SpawnedGraves.storage();
        if (storage == null) return;

        SpawnedGraves.PendingRemoval removal;
        while ((removal = SpawnedGraves.pollRemoval()) != null) {
            try {
                storage.remove(removal.storageId(), removal.reason());
            } catch (Exception ex) {
                LogUtils.error("failed to remove grave {} from storage", removal.storageId(), ex);
            }
        }

        for (Grave grave : SpawnedGraves.getGraves()) {
            GraveSnapshot snap = grave.snapshot();
            if (snap.version() == grave.lastPersistedVersion()) continue;

            persistOne(grave, snap, storage);
        }
    }

    private static void persistOne(@NotNull Grave grave, @NotNull GraveSnapshot snap, @NotNull GraveStorage storage) {
        try {
            GraveRecord record = new GraveRecord(
                    grave.storageId(),
                    grave.getPlayer().getUniqueId(),
                    grave.getPlayerName(),
                    Serializers.LOCATION.serialize(grave.getLocation()),
                    snap.serializedItems(),
                    currentDataVersion(),
                    snap.storedXP(),
                    grave.getSpawned(),
                    null,
                    null
            );

            long assignedId = storage.save(record);
            grave.assignStorageId(assignedId);
            grave.markPersisted(snap.version());
        } catch (Exception ex) {
            LogUtils.error("failed to save a grave to storage", ex);
        }
    }

    private static int currentDataVersion() {
        try {
            return org.bukkit.Bukkit.getUnsafe().getDataVersion();
        } catch (Throwable t) {
            return -1;
        }
    }
}
