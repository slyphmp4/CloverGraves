package com.slyph.clovergraves.grave;

import com.slyph.clovergraves.api.events.GraveInteractEvent;
import com.slyph.clovergraves.api.events.GraveOpenEvent;
import com.slyph.clovergraves.config.GraveSettings;
import com.slyph.clovergraves.config.HologramSettings;
import com.slyph.clovergraves.grave.hologram.GraveHologram;
import com.slyph.clovergraves.grave.hologram.GraveHologramFactory;
import com.slyph.clovergraves.schedulers.CloverScheduler;
import com.slyph.clovergraves.schedulers.CloverTask;
import com.slyph.clovergraves.storage.EndReason;
import com.slyph.clovergraves.utils.BlacklistUtils;
import com.slyph.clovergraves.utils.ExperienceUtils;
import com.slyph.clovergraves.utils.InventoryOrderSnapshot;
import com.slyph.clovergraves.utils.InventoryUtils;
import com.slyph.clovergraves.utils.LocationUtils;
import com.slyph.clovergraves.utils.TextFormatter;
import com.slyph.clovergraves.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
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
    private static final long TICK_PERIOD = 2L;
    private static final float HOLOGRAM_LINE_SPACING = 0.3f;

    private final long spawned;
    private final Location location;
    private final BlockKey blockKey;
    private final OfflinePlayer player;
    private final String playerName;
    private final int rows;
    private final GraveContents contents;
    private final GraveInventoryHolder holder;
    private final ArmorStand entity;
    private final CloverTask tickTask;
    private final AtomicBoolean removed = new AtomicBoolean(false);
    private final Map<UUID, Long> lastProtectionNotice = new ConcurrentHashMap<>();

    private GraveHologram hologram;
    private String lastHologramText = "";
    private long lastHologramUpdateAt;
    private volatile long storageId = -1;
    private volatile long lastPersistedVersion = -1;

    public Grave(@NotNull Location loc, @NotNull OfflinePlayer offlinePlayer, @NotNull List<ItemStack> items,
                 int storedXP, long date, @NotNull InventoryOrderSnapshot orderSnapshot) {
        List<ItemStack> filtered = new ArrayList<>(items);
        filtered.removeIf(it -> it == null || BlacklistUtils.isBlacklisted(it));
        filtered.replaceAll(ItemStack::clone);
        filtered = InventoryUtils.reorderInventory(orderSnapshot, filtered);

        List<ItemStack> overflow = List.of();
        if (filtered.size() > InventoryUtils.MAX_SLOTS) {
            overflow = new ArrayList<>(filtered.subList(InventoryUtils.MAX_SLOTS, filtered.size()));
            filtered = new ArrayList<>(filtered.subList(0, InventoryUtils.MAX_SLOTS));
        }

        location = LocationUtils.getCenterOf(loc, true, false);
        LocationUtils.clampLocation(location);
        blockKey = BlockKey.of(location);
        player = offlinePlayer;
        playerName = offlinePlayer.getName() == null ? LANG.getString("unknown-player", "???") : offlinePlayer.getName();
        spawned = date;
        rows = InventoryUtils.getRequiredRows(filtered.size());
        String title = TextFormatter.formatToString(LANG.getString("gui-name", "&0%player%'s Grave").replace("%player%", playerName));
        contents = new GraveContents(location, title, filtered, storedXP);
        holder = new GraveInventoryHolder(this);

        for (ItemStack item : overflow) {
            location.getWorld().dropItem(location.clone(), item);
        }

        Player onlinePlayer = offlinePlayer.getPlayer();
        if (onlinePlayer != null && LANG.getBoolean("death-message.enabled", false)) {
            MESSAGEUTILS.sendLang(onlinePlayer, "death-message.message", Map.of(
                    "%world%", LocationUtils.getWorldName(location.getWorld()),
                    "%x%", String.valueOf(location.getBlockX()),
                    "%y%", String.valueOf(location.getBlockY()),
                    "%z%", String.valueOf(location.getBlockZ())
            ));
        }

        Location headLocation = location.clone().add(0, 1 + CONFIG.getFloat("head-height", -1.2f), 0);
        entity = (ArmorStand) location.getWorld().spawnEntity(headLocation, EntityType.ARMOR_STAND);
        entity.setVisible(false);
        entity.setSmall(true);
        entity.setBasePlate(false);
        entity.setGravity(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(false);
        entity.setCollidable(false);
        entity.setCanPickupItems(false);
        if (entity.getEquipment() != null) entity.getEquipment().setHelmet(Utils.getPlayerHead(offlinePlayer));

        float yaw = CONFIG.getBoolean("rotate-head-360", true)
                ? location.getYaw()
                : LocationUtils.getNearestDirection(location.getYaw());
        entity.setRotation(yaw, 0f);

        contents.refreshSnapshot();
        updateHologram();
        tickTask = CloverScheduler.get().runTimerAt(location, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public void tick() {
        if (removed.get()) return;
        contents.closeViewIfEmpty();
        contents.refreshSnapshot();

        GraveSettings settings = GraveSettings.current();
        GraveSnapshot snapshot = contents.snapshot();
        boolean outOfTime = settings.despawnTimeSeconds() != -1
                && settings.despawnTimeSeconds() * 1_000L <= System.currentTimeMillis() - spawned;
        boolean emptyDespawn = settings.despawnWhenEmpty() && snapshot.empty();

        if (outOfTime || emptyDespawn) {
            remove(outOfTime ? EndReason.EXPIRED : EndReason.LOOTED);
            return;
        }

        if (settings.autoRotationEnabled() && entity.isValid()) {
            Location current = entity.getLocation();
            entity.setRotation(current.getYaw() + settings.autoRotationSpeed(), current.getPitch());
        }

        updateHologramText(false);
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

    public void interact(@NotNull Player opener, EquipmentSlot slot) {
        if (slot != EquipmentSlot.HAND) return;
        performInteraction(opener, false);
    }

    public void leftClick(@NotNull Player opener) {
        performInteraction(opener, opener.isSneaking());
    }

    private void performInteraction(@NotNull Player opener, boolean instantPickupRequested) {
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

        GraveInteractEvent interactEvent = new GraveInteractEvent(opener, this);
        Bukkit.getPluginManager().callEvent(interactEvent);
        if (interactEvent.isCancelled()) return;

        if (instantPickupRequested) {
            if (opener.getGameMode() == GameMode.SPECTATOR) return;
            if (!settings.enableInstantPickup()) return;
            if (settings.instantPickupOnlyOwn() && !isOwner) return;
            instantPickup(opener, settings);
            return;
        }

        GraveOpenEvent openEvent = new GraveOpenEvent(opener, this);
        Bukkit.getPluginManager().callEvent(openEvent);
        if (openEvent.isCancelled()) return;

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

        long remaining = Math.max(0, settings.protectionSeconds() * 1_000L - (now - spawned));
        MESSAGEUTILS.sendLang(opener, "interact.protected", Map.of("%time%", TextFormatter.formatTime(remaining)));
    }

    private void transferXP(@NotNull Player opener) {
        int xp = contents.takeXP();
        if (xp != 0) ExperienceUtils.changeExp(opener, xp);
    }

    private void instantPickup(@NotNull Player opener, @NotNull GraveSettings settings) {
        transferXP(opener);

        PlayerInventory inventory = opener.getInventory();
        ItemStack[] snapshot = contents.items().clone();
        boolean changed = false;

        for (int i = 0; i < snapshot.length; i++) {
            ItemStack item = snapshot[i];
            if (item == null || item.getType().isAir()) continue;

            if (settings.autoEquipArmor()) {
                Material material = item.getType();
                if (isSlotEmpty(inventory.getHelmet()) && Utils.isHelmet(material)) {
                    inventory.setHelmet(item);
                    snapshot[i] = null;
                    changed = true;
                    continue;
                }
                if (isSlotEmpty(inventory.getChestplate()) && Utils.isChestplate(material)) {
                    inventory.setChestplate(item);
                    snapshot[i] = null;
                    changed = true;
                    continue;
                }
                if (isSlotEmpty(inventory.getLeggings()) && Utils.isLeggings(material)) {
                    inventory.setLeggings(item);
                    snapshot[i] = null;
                    changed = true;
                    continue;
                }
                if (isSlotEmpty(inventory.getBoots()) && Utils.isBoots(material)) {
                    inventory.setBoots(item);
                    snapshot[i] = null;
                    changed = true;
                    continue;
                }
            }

            Map<Integer, ItemStack> leftover = inventory.addItem(item);
            changed = true;
            snapshot[i] = leftover.isEmpty() ? null : leftover.values().iterator().next();
        }

        if (changed) {
            contents.setItems(snapshot);
            tick();
        }
    }

    private boolean isSlotEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    public void updateHologram() {
        if (hologram != null) hologram.remove();

        long now = System.currentTimeMillis();
        List<String> formatted = formatHologramLines(now);
        double height = CONFIG.getFloat("hologram-height", 0.75f) + 1;
        Location topLocation = location.clone().add(0, height, 0);
        HologramSettings settings = HologramSettings.parse(CONFIG.getSection("holograms"));

        hologram = GraveHologramFactory.create(topLocation, formatted, settings, HOLOGRAM_LINE_SPACING);
        lastHologramText = String.join("\n", formatted);
        lastHologramUpdateAt = now;
    }

    private void updateHologramText(boolean force) {
        if (hologram == null) {
            updateHologram();
            return;
        }
        if (!hologram.isValid()) {
            updateHologram();
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && now - lastHologramUpdateAt < 1_000L) return;
        lastHologramUpdateAt = now;

        List<String> formatted = formatHologramLines(now);
        String text = String.join("\n", formatted);
        if (force || !text.equals(lastHologramText)) {
            hologram.setLines(formatted);
            lastHologramText = text;
        }
    }

    @NotNull
    private List<String> formatHologramLines(long now) {
        GraveSnapshot snapshot = contents.snapshot();
        int despawnTime = CONFIG.getInt("despawn-time-seconds", 1800);
        long remaining = despawnTime == -1 ? now - spawned : Math.max(0L, despawnTime * 1_000L - (now - spawned));

        List<String> formatted = new ArrayList<>();
        for (String line : LANG.getStringList("hologram")) {
            String replaced = line
                    .replace("%player%", playerName)
                    .replace("%item%", String.valueOf(snapshot.itemCount()))
                    .replace("%xp%", String.valueOf(snapshot.storedXP()))
                    .replace("%despawn-time%", TextFormatter.formatTime(remaining));
            formatted.add(TextFormatter.formatToString(replaced));
        }
        return formatted;
    }

    public int countItems() {
        return contents.countItems();
    }

    public void remove() {
        remove(EndReason.REMOVED);
    }

    public void remove(@NotNull EndReason reason) {
        if (!removed.compareAndSet(false, true)) return;

        Runnable action = () -> {
            tickTask.cancel();
            SpawnedGraves.removeGrave(this, reason);
            removeInventory();
            if (entity != null) entity.remove();
            if (hologram != null) hologram.remove();
        };

        if (CloverScheduler.get().isOwnedByCurrentRegion(location)) action.run();
        else CloverScheduler.get().runAt(location, action);
    }

    public void removeInventory() {
        closeAllViewers();
        ItemStack[] drained = contents.drainItems();
        int xp = contents.takeXP();
        contents.refreshSnapshot();

        GraveSettings settings = GraveSettings.current();
        if (settings.dropItems()) {
            for (ItemStack item : drained) {
                if (item == null || item.getType().isAir()) continue;
                Item dropped = location.getWorld().dropItem(location.clone(), item);
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
        for (HumanEntity viewer : new ArrayList<>(view.getViewers())) closeFor(viewer);
    }

    private void closeFor(@NotNull HumanEntity viewer) {
        CloverScheduler.get().run(viewer, task -> viewer.closeInventory(), () -> {
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

    public long storageId() {
        return storageId;
    }

    public void assignStorageId(long id) {
        storageId = id;
    }

    public long lastPersistedVersion() {
        return lastPersistedVersion;
    }

    public void markPersisted(long version) {
        lastPersistedVersion = version;
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

    @NotNull
    public Inventory getGui() {
        return contents.openFor(holder, rows);
    }

    public int getStoredXP() {
        return contents.storedXP();
    }

    public ArmorStand getEntity() {
        return entity;
    }

    public GraveHologram getHologram() {
        return hologram;
    }

    public String getPlayerName() {
        return playerName;
    }
}
