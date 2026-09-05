package com.slyph.clovergraves.grave;

import com.artillexstudios.axapi.hologram.Hologram;
import com.artillexstudios.axapi.hologram.HologramType;
import com.artillexstudios.axapi.hologram.HologramTypes;
import com.artillexstudios.axapi.hologram.page.HologramPage;
import com.artillexstudios.axapi.items.WrappedItemStack;
import com.artillexstudios.axapi.nms.NMSHandlers;
import com.artillexstudios.axapi.packet.wrapper.serverbound.ServerboundInteractWrapper;
import com.artillexstudios.axapi.packetentity.PacketEntity;
import com.artillexstudios.axapi.packetentity.meta.entity.ArmorStandMeta;
import com.artillexstudios.axapi.packetentity.meta.entity.TextDisplayMeta;
import com.artillexstudios.axapi.scheduler.ScheduledTask;
import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.EquipmentSlot;
import com.artillexstudios.axapi.utils.StringUtils;
import com.slyph.clovergraves.api.events.GraveInteractEvent;
import com.slyph.clovergraves.api.events.GraveOpenEvent;
import com.slyph.clovergraves.config.GraveSettings;
import com.slyph.clovergraves.config.HologramSettings;
import com.slyph.clovergraves.storage.EndReason;
import com.slyph.clovergraves.utils.BlacklistUtils;
import com.slyph.clovergraves.utils.ExperienceUtils;
import com.slyph.clovergraves.utils.InventoryOrderSnapshot;
import com.slyph.clovergraves.utils.InventoryUtils;
import com.slyph.clovergraves.utils.LocationUtils;
import com.slyph.clovergraves.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.slyph.clovergraves.AxGraves.CONFIG;
import static com.slyph.clovergraves.AxGraves.LANG;
import static com.slyph.clovergraves.AxGraves.MESSAGEUTILS;

public class Grave {
    private static final Vector ZERO_VECTOR = new Vector(0, 0, 0);
    private static final long TICK_PERIOD = 2L; // 100ms, matches the old EXECUTOR tick rate
    private static final float HOLOGRAM_LINE_SPACING = 0.3f;
    private static final int HOLOGRAM_LINE_WIDTH = 1000;

    private final long spawned;
    private final Location location;
    private final BlockKey blockKey;
    private final OfflinePlayer player;
    private final String playerName;
    private final int rows;
    private final GraveContents contents;
    private final GraveInventoryHolder holder;
    private final PacketEntity entity;
    private final ScheduledTask tickTask;
    private final AtomicBoolean removed = new AtomicBoolean(false);
    private final Map<UUID, Long> lastProtectionNotice = new ConcurrentHashMap<>();

    private Hologram hologram;

    // storage bookkeeping - touched only by the single-threaded save executor, see SaveGraves
    private volatile long storageId = -1;
    private volatile long lastPersistedVersion = -1;

    public Grave(@NotNull Location loc, @NotNull OfflinePlayer offlinePlayer, @NotNull List<ItemStack> items,
                 int storedXP, long date, @NotNull InventoryOrderSnapshot orderSnapshot) {
        List<ItemStack> filtered = new ArrayList<>(items);
        filtered.removeIf(it -> it == null || BlacklistUtils.isBlacklisted(it));
        filtered.replaceAll(ItemStack::clone);
        filtered = InventoryUtils.reorderInventory(orderSnapshot, filtered);

        // a grave inventory caps at 6 rows (54 slots); anything beyond that used to make
        // Bukkit.createInventory throw with nothing catching it. Overflow is dropped on the
        // ground next to the grave instead of being silently discarded.
        List<ItemStack> overflow = List.of();
        if (filtered.size() > InventoryUtils.MAX_SLOTS) {
            overflow = new ArrayList<>(filtered.subList(InventoryUtils.MAX_SLOTS, filtered.size()));
            filtered = new ArrayList<>(filtered.subList(0, InventoryUtils.MAX_SLOTS));
        }

        this.location = LocationUtils.getCenterOf(loc, true, false);
        LocationUtils.clampLocation(location);
        this.blockKey = BlockKey.of(location);

        this.player = offlinePlayer;
        this.playerName = offlinePlayer.getName() == null ? LANG.getString("unknown-player", "???") : offlinePlayer.getName();
        this.spawned = date;

        this.rows = InventoryUtils.getRequiredRows(filtered.size());
        String title = StringUtils.formatToString(LANG.getString("gui-name").replace("%player%", playerName));
        this.contents = new GraveContents(location, title, filtered, storedXP);
        this.holder = new GraveInventoryHolder(this);

        for (ItemStack it : overflow) {
            location.getWorld().dropItem(location.clone(), it);
        }

        Player pl = offlinePlayer.getPlayer();
        if (pl != null && LANG.getBoolean("death-message.enabled", false)) {
            MESSAGEUTILS.sendLang(pl, "death-message.message", Map.of(
                    "%world%", LocationUtils.getWorldName(location.getWorld()),
                    "%x%", "" + location.getBlockX(),
                    "%y%", "" + location.getBlockY(),
                    "%z%", "" + location.getBlockZ()));
        }

        this.entity = NMSHandlers.getNmsHandler().createEntity(EntityType.ARMOR_STAND,
                location.clone().add(0, 1 + CONFIG.getFloat("head-height", -1.2f), 0));
        entity.setItem(EquipmentSlot.HELMET, WrappedItemStack.wrap(Utils.getPlayerHead(offlinePlayer)));
        final ArmorStandMeta meta = (ArmorStandMeta) entity.meta();
        meta.small(true);
        meta.invisible(true);
        meta.setNoBasePlate(false);
        entity.spawn();

        if (CONFIG.getBoolean("rotate-head-360", true)) {
            entity.location().setYaw(location.getYaw());
        } else {
            entity.location().setYaw(LocationUtils.getNearestDirection(location.getYaw()));
        }
        entity.teleport(entity.location());

        entity.onInteract(event -> Scheduler.get().runAt(location, task -> interact(event.getPlayer(), event.getHand())));

        updateHologram();

        this.tickTask = Scheduler.get().runTimerAt(location, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    /**
     * Runs every 100ms on the region owning {@link #location} - replaces the old shared
     * {@code TickGraves} loop that ran off an async executor and read the live Bukkit inventory
     * from it. Refreshes the published {@link GraveSnapshot} (so off-thread readers such as the
     * storage flush always see an at-most-one-tick-old view), handles expiry/auto-rotation, and
     * folds in the "close distant viewers" check that used to be a separate global timer.
     */
    public void tick() {
        contents.closeViewIfEmpty();
        contents.refreshSnapshot();

        GraveSettings settings = GraveSettings.current();
        GraveSnapshot snap = contents.snapshot();

        boolean outOfTime = settings.despawnTimeSeconds() != -1
                && settings.despawnTimeSeconds() * 1_000L <= (System.currentTimeMillis() - spawned);
        boolean emptyDespawn = settings.despawnWhenEmpty() && snap.empty();

        if (outOfTime || emptyDespawn) {
            remove(outOfTime ? EndReason.EXPIRED : EndReason.LOOTED);
            return;
        }

        if (settings.autoRotationEnabled()) {
            entity.location().setYaw(entity.location().getYaw() + settings.autoRotationSpeed());
            entity.teleport(entity.location());
        }

        closeDistantViewers(settings);
    }

    private void closeDistantViewers(@NotNull GraveSettings settings) {
        Inventory view = contents.viewIfOpen();
        if (view == null) return;

        for (HumanEntity viewer : new ArrayList<>(view.getViewers())) {
            boolean tooFar = !Objects.equals(viewer.getWorld(), location.getWorld())
                    || viewer.getLocation().distanceSquared(location) > settings.interactRadiusSquared();
            if (tooFar) closeFor(viewer);
        }
    }

    public void interact(@NotNull Player opener, ServerboundInteractWrapper.InteractionHand slot) {
        GraveSettings settings = GraveSettings.current();

        if (!opener.getWorld().equals(location.getWorld())) return;
        if (opener.getLocation().distanceSquared(location) > settings.interactRadiusSquared()) return;

        boolean isOwner = opener.getUniqueId().equals(player.getUniqueId());
        boolean adminBypass = opener.hasPermission("axgraves.admin");

        if (settings.interactOnlyOwn() && !isOwner && !adminBypass) {
            MESSAGEUTILS.sendLang(opener, "interact.not-your-grave");
            return;
        }

        if (!isOwner && !adminBypass && !opener.hasPermission("axgraves.protection.bypass") && isProtected(settings)) {
            notifyProtected(opener, settings);
            return;
        }

        final GraveInteractEvent graveInteractEvent = new GraveInteractEvent(opener, this);
        Bukkit.getPluginManager().callEvent(graveInteractEvent);
        if (graveInteractEvent.isCancelled()) return;

        if (slot != null && slot.equals(ServerboundInteractWrapper.InteractionHand.MAIN_HAND) && opener.isSneaking()) {
            if (opener.getGameMode() == GameMode.SPECTATOR) return;
            if (!settings.enableInstantPickup()) return;
            if (settings.instantPickupOnlyOwn() && !isOwner) return;

            instantPickup(opener, settings);
            return;
        }

        final GraveOpenEvent graveOpenEvent = new GraveOpenEvent(opener, this);
        Bukkit.getPluginManager().callEvent(graveOpenEvent);
        if (graveOpenEvent.isCancelled()) return;

        // XP is granted here - after every permission/cancellation gate - rather than
        // unconditionally at the top of this method as before, so a plugin cancelling
        // GraveOpenEvent (or instant-pickup being disabled/owner-only) actually protects it too.
        transferXP(opener);
        opener.openInventory(contents.openFor(holder, rows));
    }

    private boolean isProtected(@NotNull GraveSettings settings) {
        if (settings.protectionSeconds() <= 0) return false;
        return System.currentTimeMillis() - spawned < settings.protectionSeconds() * 1_000L;
    }

    private void notifyProtected(@NotNull Player opener, @NotNull GraveSettings settings) {
        long now = System.currentTimeMillis();
        long cooldownMillis = settings.protectionMessageCooldownSeconds() * 1_000L;

        Long last = lastProtectionNotice.get(opener.getUniqueId());
        if (last != null && now - last < cooldownMillis) return;
        lastProtectionNotice.put(opener.getUniqueId(), now);

        long remainingMillis = Math.max(0, settings.protectionSeconds() * 1_000L - (now - spawned));
        MESSAGEUTILS.sendLang(opener, "interact.protected", Map.of("%time%", StringUtils.formatTime(remainingMillis)));
    }

    private void transferXP(@NotNull Player opener) {
        int xp = contents.takeXP();
        if (xp != 0) {
            ExperienceUtils.changeExp(opener, xp);
        }
    }

    private void instantPickup(@NotNull Player opener, @NotNull GraveSettings settings) {
        transferXP(opener);

        PlayerInventory inventory = opener.getInventory();
        ItemStack[] snapshot = contents.items();
        boolean changed = false;

        for (int i = 0; i < snapshot.length; i++) {
            ItemStack it = snapshot[i];
            if (it == null || it.getType().isAir()) continue;

            if (settings.autoEquipArmor()) {
                Material material = it.getType();
                if (isSlotEmpty(inventory.getHelmet()) && Utils.isHelmet(material)) {
                    inventory.setHelmet(it);
                    snapshot[i] = null;
                    changed = true;
                    continue;
                }
                if (isSlotEmpty(inventory.getChestplate()) && Utils.isChestplate(material)) {
                    inventory.setChestplate(it);
                    snapshot[i] = null;
                    changed = true;
                    continue;
                }
                if (isSlotEmpty(inventory.getLeggings()) && Utils.isLeggings(material)) {
                    inventory.setLeggings(it);
                    snapshot[i] = null;
                    changed = true;
                    continue;
                }
                if (isSlotEmpty(inventory.getBoots()) && Utils.isBoots(material)) {
                    inventory.setBoots(it);
                    snapshot[i] = null;
                    changed = true;
                    continue;
                }
            }

            Map<Integer, ItemStack> leftover = inventory.addItem(it);
            changed = true;
            snapshot[i] = leftover.isEmpty() ? null : leftover.values().iterator().next();
        }

        if (changed) {
            contents.setItems(snapshot);
            tick();
        }
    }

    private boolean isSlotEmpty(ItemStack item) {
        if (item == null) return true;
        return item.getType().isAir();
    }

    public void updateHologram() {
        if (hologram != null) hologram.remove();

        List<String> lines = LANG.getStringList("hologram");

        double hologramHeight = CONFIG.getFloat("hologram-height", 0.75f) + 1;
        hologram = new Hologram(location.clone().add(0, getNewHeight(hologramHeight, lines.size(), HOLOGRAM_LINE_SPACING), 0));

        HologramPage<String, HologramType<String>> page = hologram.createPage(HologramTypes.TEXT);
        page.getParameters().withParameter(Grave.class, this);

        HologramSettings hs = HologramSettings.parse(CONFIG.getSection("holograms"));
        page.setEntityMetaHandler(m -> {
            TextDisplayMeta meta = (TextDisplayMeta) m;
            meta.seeThrough(hs.seeThrough());
            meta.shadow(hs.shadow());
            meta.alignment(hs.alignment());
            meta.backgroundColor(hs.backgroundColor());
            meta.lineWidth(HOLOGRAM_LINE_WIDTH);
            meta.billboardConstrain(hs.billboard());
        });

        page.setContent(String.join("<reset><br>", lines));
        page.spawn();
    }

    private static double getNewHeight(double y, int lines, float lineHeight) {
        return y - lineHeight * (lines - 1) + 0.25;
    }

    public int countItems() {
        return contents.countItems();
    }

    public void remove() {
        remove(EndReason.REMOVED);
    }

    public void remove(@NotNull EndReason reason) {
        if (!removed.compareAndSet(false, true)) return;

        Runnable runnable = () -> {
            if (tickTask != null) tickTask.cancel();
            SpawnedGraves.removeGrave(this, reason);
            removeInventory();

            if (entity != null) entity.remove();
            if (hologram != null) hologram.remove();
        };

        if (Scheduler.get().isOwnedByCurrentRegion(location)) runnable.run();
        else Scheduler.get().runAt(location, runnable);
    }

    public void removeInventory() {
        closeAllViewers();

        // drain (and clear) contents first, then drop/publish - so a save that races this can
        // never persist items that are simultaneously landing on the ground.
        ItemStack[] drained = contents.drainItems();
        int xp = contents.takeXP();
        contents.refreshSnapshot();

        GraveSettings settings = GraveSettings.current();
        if (settings.dropItems()) {
            for (ItemStack it : drained) {
                if (it == null || it.getType().isAir()) continue;
                Item dropped = location.getWorld().dropItem(location.clone(), it);
                if (!settings.droppedItemVelocity()) dropped.setVelocity(ZERO_VECTOR);
            }
        }

        if (xp != 0) {
            ExperienceOrb orb = (ExperienceOrb) location.getWorld().spawnEntity(location, EntityType.EXPERIENCE_ORB);
            orb.setExperience(xp);
        }
    }

    private void closeAllViewers() {
        Inventory view = contents.viewIfOpen();
        if (view == null) return;
        for (HumanEntity viewer : new ArrayList<>(view.getViewers())) {
            closeFor(viewer);
        }
    }

    /**
     * Closes the grave's GUI for {@code viewer} by dispatching to the region that owns the
     * *viewer*, not the grave. The previous implementation ran this on the grave's region and
     * then called {@code viewer.closeInventory()} directly - safe on non-Folia, but a genuine
     * cross-region entity access on Folia whenever the viewer had walked into a different region
     * than the grave (exactly the case this is used for: closing the GUI on players who wandered
     * too far away).
     */
    private void closeFor(@NotNull HumanEntity viewer) {
        Scheduler.get().run(viewer, task -> viewer.closeInventory(), () -> {
        });
    }

    @NotNull
    public GraveContents contents() {
        return contents;
    }

    @NotNull
    public GraveSnapshot snapshot() {
        return contents.snapshot();
    }

    @NotNull
    public BlockKey getBlockKey() {
        return blockKey;
    }

    public boolean isRemoved() {
        return removed.get();
    }

    /** Storage bookkeeping - touched only by the single-threaded save executor, see SaveGraves. */
    public long storageId() {
        return storageId;
    }

    public void assignStorageId(long id) {
        this.storageId = id;
    }

    public long lastPersistedVersion() {
        return lastPersistedVersion;
    }

    public void markPersisted(long version) {
        this.lastPersistedVersion = version;
    }

    public Location getLocation() {
        return location;
    }

    public OfflinePlayer getPlayer() {
        return player;
    }

    public long getSpawned() {
        return spawned;
    }

    /**
     * Materializes and returns the grave's Bukkit inventory. Kept for external API/backwards
     * compatibility; like every other {@link GraveContents} mutator, only call this from the
     * region owning {@link #getLocation()}.
     */
    @NotNull
    public Inventory getGui() {
        return contents.openFor(holder, rows);
    }

    public int getStoredXP() {
        return contents.storedXP();
    }

    public PacketEntity getEntity() {
        return entity;
    }

    public Hologram getHologram() {
        return hologram;
    }

    public String getPlayerName() {
        return playerName;
    }
}
