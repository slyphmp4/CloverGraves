package com.slyph.clovergraves.grave;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.logging.LogUtils;
import com.slyph.clovergraves.storage.ItemSerialization;
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

    public void syncFromView() {
        assertOwned();
        if (view == null) return;
        this.items = view.getContents();
        bumpVersion();
    }

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

    public int takeXP() {
        assertOwned();
        int taken = storedXP;
        storedXP = 0;
        if (taken != 0) bumpVersion();
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
        items = new ItemStack[0];
        if (view != null) view.clear();
        bumpVersion();
        return drained;
    }

    private void bumpVersion() {
        version.incrementAndGet();
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
}
