package com.slyph.clovergraves.grave;

import com.slyph.clovergraves.config.GraveSettings;
import com.slyph.clovergraves.schedulers.CloverScheduler;
import com.slyph.clovergraves.schedulers.CloverTask;
import com.slyph.clovergraves.storage.EndReason;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GraveLifecycleService {
    private static final long ROTATION_PERIOD_TICKS = 2L;
    private static final long VIEW_PERIOD_TICKS = 5L;
    private static final long HOLOGRAM_PERIOD_TICKS = 20L;

    private static volatile GraveLifecycleService instance;

    private final PriorityQueue<ExpiryEntry> expiries = new PriorityQueue<>();
    private final Map<Grave, Long> expiryByGrave = new HashMap<>();
    private final Set<Grave> openViews = ConcurrentHashMap.newKeySet();
    private final CloverTask rotationTask;
    private final CloverTask viewTask;
    private final CloverTask hologramTask;

    private GraveLifecycleService() {
        rotationTask = CloverScheduler.get().runTimer(
                this::rotateActiveGraves,
                ROTATION_PERIOD_TICKS,
                ROTATION_PERIOD_TICKS
        );
        viewTask = CloverScheduler.get().runTimer(
                this::maintainOpenViewsAndExpiries,
                VIEW_PERIOD_TICKS,
                VIEW_PERIOD_TICKS
        );
        hologramTask = CloverScheduler.get().runTimer(
                this::updateHolograms,
                HOLOGRAM_PERIOD_TICKS,
                HOLOGRAM_PERIOD_TICKS
        );
    }

    public static void init() {
        if (instance != null) instance.shutdown();
        instance = new GraveLifecycleService();
    }

    @NotNull
    public static GraveLifecycleService get() {
        GraveLifecycleService service = instance;
        if (service == null) throw new IllegalStateException("GraveLifecycleService is not initialized");
        return service;
    }

    public void register(@NotNull Grave grave) {
        scheduleExpiry(grave);
    }

    public void unregister(@NotNull Grave grave) {
        expiryByGrave.remove(grave);
        openViews.remove(grave);
        if (expiries.size() > expiryByGrave.size() * 2L + 128) {
            expiries.removeIf(entry -> !expiryByGrave.containsKey(entry.grave()));
        }
    }

    public void markViewOpen(@NotNull Grave grave) {
        if (!grave.isRemoved()) openViews.add(grave);
    }

    public void reload() {
        expiries.clear();
        expiryByGrave.clear();
        for (Grave grave : SpawnedGraves.getGraves()) scheduleExpiry(grave);
    }

    public void shutdown() {
        rotationTask.cancel();
        viewTask.cancel();
        hologramTask.cancel();
        expiries.clear();
        expiryByGrave.clear();
        openViews.clear();
        if (instance == this) instance = null;
    }

    private void scheduleExpiry(@NotNull Grave grave) {
        int despawnSeconds = GraveSettings.current().despawnTimeSeconds();
        if (despawnSeconds < 0) {
            expiryByGrave.remove(grave);
            return;
        }

        long expiresAt = grave.getSpawned() + despawnSeconds * 1_000L;
        expiryByGrave.put(grave, expiresAt);
        expiries.add(new ExpiryEntry(grave, expiresAt));
    }

    private void rotateActiveGraves() {
        GraveSettings settings = GraveSettings.current();
        if (!settings.autoRotationEnabled()) return;

        for (Grave grave : SpawnedGraves.getGraves()) {
            if (!grave.isRemoved()) grave.rotateMarker(settings);
        }
    }

    private void maintainOpenViewsAndExpiries() {
        processExpiries(System.currentTimeMillis());

        GraveSettings settings = GraveSettings.current();
        for (Grave grave : openViews) {
            if (grave.isRemoved()) {
                openViews.remove(grave);
                continue;
            }

            grave.maintainOpenView(settings);
            if (!grave.hasOpenViewers()) openViews.remove(grave);
        }
    }

    private void processExpiries(long now) {
        while (!expiries.isEmpty() && expiries.peek().expiresAt() <= now) {
            ExpiryEntry entry = expiries.poll();
            Long current = expiryByGrave.get(entry.grave());
            if (current == null || current.longValue() != entry.expiresAt()) continue;

            expiryByGrave.remove(entry.grave());
            if (!entry.grave().isRemoved()) entry.grave().remove(EndReason.EXPIRED);
        }
    }

    private void updateHolograms() {
        long now = System.currentTimeMillis();
        for (Grave grave : SpawnedGraves.getGraves()) {
            if (!grave.isRemoved()) grave.updateHologramText(now, false);
        }
    }

    private record ExpiryEntry(@NotNull Grave grave, long expiresAt) implements Comparable<ExpiryEntry> {
        @Override
        public int compareTo(@NotNull ExpiryEntry other) {
            return Long.compare(expiresAt, other.expiresAt);
        }
    }
}
