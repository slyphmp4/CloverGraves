package com.slyph.clovergraves.grave;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.serializers.Serializers;
import com.artillexstudios.axapi.utils.logging.LogUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The authoritative store for a grave's items and XP.
 *
 * <p>Previously the live Bukkit {@code Inventory} itself was the source of truth, and it was
 * read from an off-thread {@code ScheduledExecutorService} ten times a second (the old tick
 * loop) and once per autosave - an unsynchronized read of a main-thread object, and on Folia a
 * cross-region one. This type inverts that: {@link #items} is the source of truth, mutated only
 * on the region that owns the grave's location, and {@link #snapshot()} publishes an immutable,
 * pre-serialized {@link GraveSnapshot} that any thread may read safely.</p>
 *
 * <p>The Bukkit {@link Inventory} ("view") is materialized lazily, only while at least one
 * player has the grave open, and is written back into {@link #items} on every accepted click/
 * drag/close via {@link #syncFromView()}.</p>
 */
public final class GraveContents {
    private final Location location;
    private final String title;
    private ItemStack[] items;
    private int storedXP;
    private Inventory view;

    private final AtomicLong version = new AtomicLong(0);
    private volatile GraveSnapshot snapshot = GraveSnapshot.INITIAL;

    public GraveContents(@NotNull Location location, @NotNull String title, @NotNull List<ItemStack> items, int storedXP) {
        this.location = location;
        this.title = title;
        this.items = items.toArray(new ItemStack[0]);
        this.storedXP = Math.max(storedXP, 0);
    }

    private void assertOwned() {
        if (!Scheduler.get().isOwnedByCurrentRegion(location)) {
            LogUtils.warn("GraveContents touched off its owning region at {} - this is a bug, please report it", location);
        }
    }

    @NotNull
    public Inventory openFor(@NotNull GraveInventoryHolder holder, int rows) {
        assertOwned();
        if (view == null) {
            view = Bukkit.createInventory(holder, rows * 9, title);
            holder.bind(view);
            view.setContents(items);
        }
        return view;
    }

    @Nullable
    public Inventory viewIfOpen() {
        return view;
    }

    /** Re-reads {@link #items} from the live view after a click/drag/close was accepted. */
    public void syncFromView() {
        assertOwned();
        if (view == null) return;
        this.items = view.getContents();
        bumpVersion();
    }

    /** Drops the Bukkit view reference once nobody is looking at it, so a looted grave holds none. */
    public void closeViewIfEmpty() {
        assertOwned();
        if (view != null && view.getViewers().isEmpty()) {
            view = null;
        }
    }

    @NotNull
    public ItemStack[] items() {
        return items;
    }

    public void setItems(@NotNull ItemStack[] newItems) {
        assertOwned();
        this.items = newItems;
        if (view != null) view.setContents(newItems);
        bumpVersion();
    }

    public int storedXP() {
        return storedXP;
    }

    /** Zeroes and returns the stored XP - used by the interact flow, which grants it to the opener. */
    public int takeXP() {
        assertOwned();
        int taken = storedXP;
        storedXP = 0;
        if (taken != 0) bumpVersion();
        return taken;
    }

    public int countItems() {
        int count = 0;
        for (ItemStack it : items) {
            if (it != null && !it.getType().isAir()) count++;
        }
        return count;
    }

    public boolean isEmpty() {
        return countItems() == 0 && storedXP == 0;
    }

    /**
     * Empties {@link #items}/{@link #storedXP} first and only then hands back what was drained,
     * so a concurrently in-flight async save can never persist items that are simultaneously
     * about to land on the ground.
     */
    @NotNull
    public ItemStack[] drainItems() {
        assertOwned();
        ItemStack[] drained = items;
        items = new ItemStack[0];
        if (view != null) view.clear();
        bumpVersion();
        return drained;
    }

    private void bumpVersion() {
        version.incrementAndGet();
    }

    /**
     * Serializes {@link #items} to NBT bytes on the calling (region-owning) thread and publishes
     * an immutable {@link GraveSnapshot} for off-thread readers. Must be called from the owning
     * region - it is invoked every tick by {@link Grave#tick()}, so off-thread readers such as
     * the periodic storage flush always see an at-most-one-tick-old snapshot without ever having
     * to hop onto the region thread themselves.
     */
    public void refreshSnapshot() {
        assertOwned();
        long v = version.get();
        if (snapshot.version() == v) return;
        byte[] serialized = Serializers.ITEM_ARRAY.serialize(items);
        int count = countItems();
        snapshot = new GraveSnapshot(v, count, storedXP, serialized, count == 0 && storedXP == 0);
    }

    @NotNull
    public GraveSnapshot snapshot() {
        return snapshot;
    }
}
