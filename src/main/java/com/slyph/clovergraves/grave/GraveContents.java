package com.slyph.clovergraves.grave;

import com.slyph.clovergraves.schedulers.CloverScheduler;
import com.slyph.clovergraves.storage.ItemSerialization;
import com.slyph.clovergraves.utils.CloverLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
        if (!CloverScheduler.get().isOwnedByCurrentRegion(location)) {
            CloverLogger.warn("GraveContents touched off the Cardboard main thread at {}", location);
        }
    }

    @NotNull
    @SuppressWarnings("deprecation")
    public Inventory openFor(@NotNull GraveInventoryHolder holder, int rows) {
        assertOwned();
        if (view == null) {
            Inventory created = Bukkit.createInventory(holder, rows * 9, title);
            if (created == null) {
                throw new IllegalStateException("Bukkit.createInventory returned null for grave inventory");
            }
            created.setContents(items);
            holder.bind(created);
            view = created;
        }
        return view;
    }

    @Nullable
    public Inventory viewIfOpen() {
        return view;
    }

    public boolean syncFromView() {
        assertOwned();
        if (view == null) return false;

        ItemStack[] next = view.getContents();
        if (sameContents(items, next)) return false;

        items = copyItems(next);
        markChanged();
        return true;
    }

    public void closeViewIfEmpty() {
        assertOwned();
        if (view != null && view.getViewers().isEmpty()) {
            syncFromView();
            view = null;
        }
    }

    @NotNull
    public ItemStack[] items() {
        return items;
    }

    public boolean setItems(@NotNull ItemStack[] newItems) {
        assertOwned();
        if (sameContents(items, newItems)) return false;

        items = copyItems(newItems);
        if (view != null) view.setContents(items);
        markChanged();
        return true;
    }

    public int storedXP() {
        return storedXP;
    }

    public int takeXP() {
        assertOwned();
        int taken = storedXP;
        if (taken == 0) return 0;

        storedXP = 0;
        markChanged();
        return taken;
    }

    public int countItems() {
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) count++;
        }
        return count;
    }

    public boolean isEmpty() {
        return countItems() == 0 && storedXP == 0;
    }

    @NotNull
    public ItemStack[] drainItems() {
        assertOwned();
        ItemStack[] drained = items;
        if (drained.length == 0) return drained;

        items = new ItemStack[0];
        if (view != null) view.clear();
        markChanged();
        return drained;
    }

    private void markChanged() {
        version.incrementAndGet();
        refreshSnapshot();
    }

    public void refreshSnapshot() {
        assertOwned();
        long currentVersion = version.get();
        if (snapshot.version() == currentVersion) return;

        byte[] serialized = ItemSerialization.serialize(items);
        int count = countItems();
        snapshot = new GraveSnapshot(currentVersion, count, storedXP, serialized, count == 0 && storedXP == 0);
    }

    @NotNull
    public GraveSnapshot snapshot() {
        return snapshot;
    }

    private boolean sameContents(@NotNull ItemStack[] left, @NotNull ItemStack[] right) {
        if (left.length != right.length) return false;
        for (int i = 0; i < left.length; i++) {
            if (!sameItem(left[i], right[i])) return false;
        }
        return true;
    }

    private static ItemStack[] copyItems(ItemStack[] source) {
        ItemStack[] copy = source.clone();
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] != null) copy[i] = copy[i].clone();
        }
        return copy;
    }

    private boolean sameItem(@Nullable ItemStack left, @Nullable ItemStack right) {
        boolean leftEmpty = left == null || left.getType().isAir();
        boolean rightEmpty = right == null || right.getType().isAir();
        if (leftEmpty || rightEmpty) return leftEmpty == rightEmpty;
        return left.equals(right);
    }
}
