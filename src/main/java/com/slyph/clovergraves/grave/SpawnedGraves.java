package com.slyph.clovergraves.grave;

import com.slyph.clovergraves.storage.EndReason;
import com.slyph.clovergraves.storage.GraveStorage;
import com.slyph.clovergraves.utils.LimitUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static com.slyph.clovergraves.AxGraves.CONFIG;

/**
 * The live grave registry. Indexes graves by {@link BlockKey} for O(1)
 * {@link com.slyph.clovergraves.listeners.PlayerInteractListener} lookups (previously an
 * O(n) scan calling {@code Location#getBlock()} per grave, which force-loads chunks) and keeps
 * an {@link AtomicInteger} count instead of the O(n) {@code Collection#size()} that used to back
 * the {@code %axgraves_grave_count%} placeholder.
 *
 * <p>Persistence itself lives in {@link GraveStorage}; this class only tracks which storage rows
 * need to be deleted, via {@link #pollRemoval()} - see {@code SaveGraves.flushDirty()}.</p>
 */
public class SpawnedGraves {
    private static final ConcurrentLinkedQueue<Grave> graves = new ConcurrentLinkedQueue<>();
    private static final Map<BlockKey, Grave> byBlock = new ConcurrentHashMap<>();
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
        count.incrementAndGet();
    }

    private static void enforceLimit(@NotNull Grave grave) {
        Player player = grave.getPlayer().getPlayer();
        int graveLimit = player == null ? CONFIG.getInt("grave-limit", -1) : LimitUtils.getGraveLimit(player);
        if (graveLimit == -1) return;

        // `grave` has not been added to the queue yet, so `num` only ever counts the player's
        // *pre-existing* graves - unlike the original implementation, `oldest` starts as null
        // rather than as the brand-new grave, so a restored grave with an old timestamp can no
        // longer be selected as "oldest" and removed before it was ever added.
        Grave oldest = null;
        int num = 0;
        for (Grave existing : graves) {
            if (!existing.getPlayer().getUniqueId().equals(grave.getPlayer().getUniqueId())) continue;
            num++;
            if (oldest == null || existing.getSpawned() < oldest.getSpawned()) oldest = existing;
        }

        if (num >= graveLimit && oldest != null) oldest.remove(EndReason.LIMIT);
    }

    /** Removes {@code grave} from the live registry and, if it was ever persisted, queues its storage row for deletion. */
    public static void removeGrave(@NotNull Grave grave, @NotNull EndReason reason) {
        if (graves.remove(grave)) count.decrementAndGet();
        byBlock.remove(grave.getBlockKey(), grave);

        if (grave.storageId() > 0) {
            pendingRemovals.add(new PendingRemoval(grave.storageId(), reason));
        }
    }

    @Nullable
    public static PendingRemoval pollRemoval() {
        return pendingRemovals.poll();
    }

    @Nullable
    public static Grave getGrave(@NotNull BlockKey key) {
        return byBlock.get(key);
    }

    @NotNull
    public static ConcurrentLinkedQueue<Grave> getGraves() {
        return graves;
    }

    public static int count() {
        return count.get();
    }
}
