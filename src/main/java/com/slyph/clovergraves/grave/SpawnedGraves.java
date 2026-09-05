package com.slyph.clovergraves.grave;

import com.slyph.clovergraves.storage.EndReason;
import com.slyph.clovergraves.storage.GraveStorage;
import com.slyph.clovergraves.utils.LimitUtils;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static com.slyph.clovergraves.AxGraves.CONFIG;

public class SpawnedGraves {
    private static final ConcurrentLinkedQueue<Grave> graves = new ConcurrentLinkedQueue<>();
    private static final Map<BlockKey, Grave> byBlock = new ConcurrentHashMap<>();
    private static final Map<UUID, Grave> byEntity = new ConcurrentHashMap<>();
    private static final AtomicInteger count = new AtomicInteger();
    private static final Queue<PendingRemoval> pendingRemovals = new ConcurrentLinkedQueue<>();

    private static volatile GraveStorage storage;

    public record PendingRemoval(long storageId, @NotNull EndReason reason) {
    }

    public static void setStorage(@Nullable GraveStorage newStorage) {
        storage = newStorage;
    }

    @Nullable
    public static GraveStorage storage() {
        return storage;
    }

    public static void addGrave(@NotNull Grave grave) {
        enforceLimit(grave);

        graves.add(grave);
        byBlock.put(grave.getBlockKey(), grave);
        registerEntity(grave.getEntity(), grave);
        registerEntity(grave.getInteractionEntity(), grave);
        count.incrementAndGet();
    }

    private static void enforceLimit(@NotNull Grave grave) {
        Player player = grave.getPlayer().getPlayer();
        int graveLimit = player == null ? CONFIG.getInt("grave-limit", -1) : LimitUtils.getGraveLimit(player);
        if (graveLimit == -1) return;

        Grave oldest = null;
        int num = 0;
        for (Grave existing : graves) {
            if (!existing.getPlayer().getUniqueId().equals(grave.getPlayer().getUniqueId())) continue;
            num++;
            if (oldest == null || existing.getSpawned() < oldest.getSpawned()) oldest = existing;
        }

        if (num >= graveLimit && oldest != null) oldest.remove(EndReason.LIMIT);
    }

    public static void removeGrave(@NotNull Grave grave, @NotNull EndReason reason) {
        if (graves.remove(grave)) count.decrementAndGet();
        byBlock.remove(grave.getBlockKey(), grave);
        unregisterEntity(grave.getEntity(), grave);
        unregisterEntity(grave.getInteractionEntity(), grave);

        if (grave.storageId() > 0) {
            pendingRemovals.add(new PendingRemoval(grave.storageId(), reason));
        }
    }

    private static void registerEntity(@Nullable Entity entity, @NotNull Grave grave) {
        if (entity != null) byEntity.put(entity.getUniqueId(), grave);
    }

    private static void unregisterEntity(@Nullable Entity entity, @NotNull Grave grave) {
        if (entity != null) byEntity.remove(entity.getUniqueId(), grave);
    }

    @Nullable
    public static PendingRemoval pollRemoval() {
        return pendingRemovals.poll();
    }

    @Nullable
    public static Grave getGrave(@NotNull BlockKey key) {
        return byBlock.get(key);
    }

    @Nullable
    public static Grave getGrave(@NotNull UUID entityId) {
        return byEntity.get(entityId);
    }

    @NotNull
    public static ConcurrentLinkedQueue<Grave> getGraves() {
        return graves;
    }

    public static int count() {
        return count.get();
    }
}
