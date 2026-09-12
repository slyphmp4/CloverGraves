package com.slyph.clovergraves.grave;

import com.slyph.clovergraves.storage.EndReason;
import com.slyph.clovergraves.storage.GraveStorage;
import com.slyph.clovergraves.utils.LimitUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.slyph.clovergraves.AxGraves.CONFIG;

public class SpawnedGraves {
    private static final Set<Grave> graves = ConcurrentHashMap.newKeySet();
    private static final Map<BlockKey, Grave> byBlock = new ConcurrentHashMap<>();
    private static final Map<UUID, Grave> byEntity = new ConcurrentHashMap<>();
    private static final Map<UUID, ConcurrentLinkedDeque<Grave>> byOwner = new ConcurrentHashMap<>();
    private static final Map<ChunkKey, Set<Grave>> byChunk = new ConcurrentHashMap<>();
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
        byOwner.computeIfAbsent(grave.getPlayer().getUniqueId(), ignored -> new ConcurrentLinkedDeque<>()).addLast(grave);
        byChunk.computeIfAbsent(grave.getChunkKey(), ignored -> ConcurrentHashMap.newKeySet()).add(grave);
        bindEntity(grave);
        GraveLifecycleService.get().register(grave);
    }

    private static void enforceLimit(@NotNull Grave grave) {
        Player player = grave.getPlayer().getPlayer();
        int graveLimit = player == null ? CONFIG.getInt("grave-limit", -1) : LimitUtils.getGraveLimit(player);
        if (graveLimit < 0) return;

        ConcurrentLinkedDeque<Grave> ownerGraves = byOwner.get(grave.getPlayer().getUniqueId());
        if (ownerGraves == null) return;

        while (ownerGraves.size() >= graveLimit && !ownerGraves.isEmpty()) {
            Grave oldest = ownerGraves.peekFirst();
            if (oldest == null) break;
            oldest.remove(EndReason.LIMIT);
        }
    }

    public static void removeGrave(@NotNull Grave grave, @NotNull EndReason reason) {
        GraveLifecycleService.get().unregister(grave);
        unbindEntity(grave);
        graves.remove(grave);
        byBlock.remove(grave.getBlockKey(), grave);

        UUID owner = grave.getPlayer().getUniqueId();
        ConcurrentLinkedDeque<Grave> ownerGraves = byOwner.get(owner);
        if (ownerGraves != null) {
            ownerGraves.remove(grave);
            if (ownerGraves.isEmpty()) byOwner.remove(owner, ownerGraves);
        }

        Set<Grave> chunkGraves = byChunk.get(grave.getChunkKey());
        if (chunkGraves != null) {
            chunkGraves.remove(grave);
            if (chunkGraves.isEmpty()) byChunk.remove(grave.getChunkKey(), chunkGraves);
        }

        if (grave.storageId() > 0) {
            pendingRemovals.add(new PendingRemoval(grave.storageId(), reason));
        }
    }

    static void bindEntity(@NotNull Grave grave) {
        if (!graves.contains(grave) || grave.getEntity() == null) return;
        byEntity.put(grave.getEntity().getUniqueId(), grave);
    }

    static void unbindEntity(@NotNull Grave grave) {
        if (grave.getEntity() == null) return;
        byEntity.remove(grave.getEntity().getUniqueId(), grave);
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
    public static Collection<Grave> getGraves() {
        return graves;
    }

    @NotNull
    public static List<Grave> getGraves(@NotNull UUID owner) {
        ConcurrentLinkedDeque<Grave> ownerGraves = byOwner.get(owner);
        return ownerGraves == null ? List.of() : List.copyOf(ownerGraves);
    }

    @NotNull
    public static List<Grave> getGraves(@NotNull ChunkKey chunkKey) {
        Set<Grave> chunkGraves = byChunk.get(chunkKey);
        return chunkGraves == null ? List.of() : List.copyOf(chunkGraves);
    }

    @Nullable
    public static Grave getLatestGrave(@NotNull UUID owner) {
        ConcurrentLinkedDeque<Grave> ownerGraves = byOwner.get(owner);
        return ownerGraves == null ? null : ownerGraves.peekLast();
    }

    public static int count() {
        return graves.size();
    }
}
