package com.slyph.clovergraves.listeners;

import com.slyph.clovergraves.AxGraves;
import com.slyph.clovergraves.api.events.GravePreSpawnEvent;
import com.slyph.clovergraves.api.events.GraveSpawnEvent;
import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.grave.placement.BlockProbe;
import com.slyph.clovergraves.grave.placement.BukkitBlockProbe;
import com.slyph.clovergraves.grave.placement.PlacementSettings;
import com.slyph.clovergraves.grave.placement.SafeLocationFinder;
import com.slyph.clovergraves.schedulers.SaveGraves;
import com.slyph.clovergraves.utils.CloverLogger;
import com.slyph.clovergraves.utils.ExperienceUtils;
import com.slyph.clovergraves.utils.InventoryOrderSnapshot;
import com.slyph.clovergraves.utils.LocationUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.slyph.clovergraves.AxGraves.CONFIG;
import static com.slyph.clovergraves.AxGraves.MESSAGEUTILS;

public class DeathListener implements Listener {
    private static List<String> disabledWorlds;
    private static List<String> blacklistedDeathCauses;
    private static boolean overrideKeepInventory;
    private static boolean overrideKeepLevel;
    private static boolean storeItems;
    private static boolean storeXP;
    private static float xpKeepPercentage;

    public static void reload() {
        disabledWorlds = CONFIG.getStringList("disabled-worlds");
        blacklistedDeathCauses = CONFIG.getStringList("blacklisted-death-causes");
        overrideKeepInventory = CONFIG.getBoolean("override-keep-inventory", true);
        overrideKeepLevel = CONFIG.getBoolean("override-keep-level", true);
        storeItems = CONFIG.getBoolean("store-items", true);
        storeXP = CONFIG.getBoolean("store-xp", true);
        xpKeepPercentage = CONFIG.getFloat("xp-keep-percentage", 1f);
    }

    public DeathListener() {
        reload();

        EventPriority priority;
        try {
            priority = EventPriority.valueOf(CONFIG.getString("death-listener-priority", "HIGHEST").toUpperCase());
        } catch (IllegalArgumentException ex) {
            CloverLogger.warn("invalid death-listener-priority; using HIGHEST");
            priority = EventPriority.HIGHEST;
        }

        EventExecutor executor = (listener, event) -> {
            if (event instanceof PlayerDeathEvent deathEvent) onDeath(deathEvent);
        };
        AxGraves.getInstance().getServer().getPluginManager().registerEvent(
                PlayerDeathEvent.class, this, priority, executor, AxGraves.getInstance(), true
        );
    }

    public void onDeath(PlayerDeathEvent event) {
        boolean debug = AxGraves.isDebugMode();
        Player player = event.getEntity();

        if (debug) CloverLogger.info("[{}] creating grave", player.getName());
        if (disabledWorlds.contains(player.getWorld().getName())) return;
        if (!player.hasPermission("axgraves.allowgraves")) return;
        if (player.getLastDamageCause() != null
                && blacklistedDeathCauses.contains(player.getLastDamageCause().getCause().name())) return;

        Location location = player.getLocation().clone().add(0, -0.5, 0);
        GravePreSpawnEvent preSpawn = new GravePreSpawnEvent(player, location);
        Bukkit.getPluginManager().callEvent(preSpawn);
        if (preSpawn.isCancelled()) return;

        relocateIfUnsafe(location, player, debug);
        InventoryOrderSnapshot orderSnapshot = InventoryOrderSnapshot.capture(player.getInventory());

        List<ItemStack> drops = new ArrayList<>();
        if (storeItems) {
            boolean store = false;
            if (!event.getKeepInventory()) {
                store = true;
                drops = new ArrayList<>(event.getDrops());
            } else if (overrideKeepInventory) {
                store = true;
                drops = Arrays.asList(player.getInventory().getContents());
                player.getInventory().clear();
            }
            if (store) event.getDrops().clear();
        }

        int xp = 0;
        if (storeXP) {
            boolean store = !event.getKeepLevel() || overrideKeepLevel;
            if (store) {
                xp = Math.round(ExperienceUtils.getExp(player) * xpKeepPercentage);
                event.setDroppedExp(0);
                if (event.getKeepLevel() && overrideKeepLevel) {
                    player.setLevel(0);
                    player.setExp(0f);
                    player.setTotalExperience(0);
                }
            }
        }

        if (drops.isEmpty() && xp == 0) return;

        try {
            Grave grave = new Grave(location, player, drops, xp, System.currentTimeMillis(), orderSnapshot);
            SpawnedGraves.addGrave(grave);
            SaveGraves.saveNow(grave);
            Bukkit.getPluginManager().callEvent(new GraveSpawnEvent(player, grave));
            if (debug) CloverLogger.info("[{}] grave created", player.getName());
        } catch (Exception ex) {
            CloverLogger.error("failed to create a grave for {}; restoring captured items and xp", player.getName(), ex);
            restoreOnFailure(player, drops, xp);
        }
    }

    private static void restoreOnFailure(Player player, List<ItemStack> drops, int xp) {
        for (ItemStack item : drops) {
            if (item == null || item.getType().isAir()) continue;
            for (ItemStack extra : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItem(player.getLocation(), extra);
            }
        }
        if (xp > 0) ExperienceUtils.changeExp(player, xp);
    }

    private static void relocateIfUnsafe(Location location, Player player, boolean debug) {
        if (!CONFIG.getBoolean("safe-placement.enabled", true)) return;
        World world = location.getWorld();
        if (world == null) return;

        PlacementSettings settings = buildPlacementSettings(world);
        BlockProbe probe = new BukkitBlockProbe(world);
        int standingY = location.getBlockY() + 1;
        SafeLocationFinder.Result result = SafeLocationFinder.find(
                probe, location.getBlockX(), standingY, location.getBlockZ(), settings
        );

        boolean spawnFallback = false;
        if (!result.found()) {
            Location spawn = world.getSpawnLocation();
            SafeLocationFinder.Result fallback = SafeLocationFinder.find(
                    probe, spawn.getBlockX(), spawn.getBlockY() + 1, spawn.getBlockZ(), settings
            );
            if (!fallback.found()) return;
            result = fallback;
            spawnFallback = true;
        }

        if (!result.relocated() && !spawnFallback) return;
        location.setX(result.x() + 0.5);
        location.setY(result.y() - 1);
        location.setZ(result.z() + 0.5);

        if (debug) {
            CloverLogger.info("[{}] grave relocated to {},{},{} after {} probes",
                    player.getName(), result.x(), result.y(), result.z(), result.probes());
        }

        if (settings.notifyOwner()) {
            MESSAGEUTILS.sendLang(player, spawnFallback ? "grave-relocated-far" : "grave-relocated", Map.of(
                    "%world%", LocationUtils.getWorldName(world),
                    "%x%", String.valueOf(result.x()),
                    "%y%", String.valueOf(result.y()),
                    "%z%", String.valueOf(result.z())
            ));
        }
    }

    @NotNull
    private static PlacementSettings buildPlacementSettings(@NotNull World world) {
        LocationUtils.HeightLimits limits = LocationUtils.getHeightLimits(world);
        return new PlacementSettings(
                true,
                CONFIG.getBoolean("safe-placement.avoid-lava", true),
                CONFIG.getBoolean("safe-placement.avoid-solid", true),
                CONFIG.getBoolean("safe-placement.avoid-nether-roof", true),
                CONFIG.getInt("safe-placement.nether-roof-y", 125),
                world.getEnvironment() == World.Environment.NETHER,
                CONFIG.getBoolean("safe-placement.require-ground-support", true),
                CONFIG.getInt("safe-placement.max-horizontal-radius", 16),
                CONFIG.getInt("safe-placement.max-vertical-distance", 128),
                (int) limits.min(),
                (int) limits.max(),
                CONFIG.getBoolean("safe-placement.notify-owner", true)
        );
    }
}
