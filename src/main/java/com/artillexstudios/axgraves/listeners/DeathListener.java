package com.artillexstudios.axgraves.listeners;

import com.artillexstudios.axapi.utils.logging.LogUtils;
import com.artillexstudios.axgraves.AxGraves;
import com.artillexstudios.axgraves.api.events.GravePreSpawnEvent;
import com.artillexstudios.axgraves.api.events.GraveSpawnEvent;
import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import com.artillexstudios.axgraves.grave.placement.BlockProbe;
import com.artillexstudios.axgraves.grave.placement.BukkitBlockProbe;
import com.artillexstudios.axgraves.grave.placement.PlacementSettings;
import com.artillexstudios.axgraves.grave.placement.SafeLocationFinder;
import com.artillexstudios.axgraves.schedulers.SaveGraves;
import com.artillexstudios.axgraves.utils.ExperienceUtils;
import com.artillexstudios.axgraves.utils.InventoryOrderSnapshot;
import com.artillexstudios.axgraves.utils.LocationUtils;
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

import static com.artillexstudios.axgraves.AxGraves.CONFIG;
import static com.artillexstudios.axgraves.AxGraves.MESSAGEUTILS;

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

        String priority = CONFIG.getString("death-listener-priority", "MONITOR");
        EventPriority eventPriority;
        try {
            eventPriority = EventPriority.valueOf(priority);
        } catch (IllegalArgumentException ex) {
            LogUtils.error("invalid event priority: {} (defaulting to MONITOR)", priority);
            eventPriority = EventPriority.MONITOR;
        }

        EventExecutor executor = (listener, event) -> {
            if (listener instanceof DeathListener && event instanceof PlayerDeathEvent deathEvent) {
                onDeath(deathEvent);
            }
        };

        AxGraves.getInstance().getServer().getPluginManager().registerEvent(
                PlayerDeathEvent.class,
                this,
                eventPriority,
                executor,
                AxGraves.getInstance(),
                true
        );
    }

    public void onDeath(PlayerDeathEvent event) {
        boolean debug = AxGraves.isDebugMode();
        Player player = event.getEntity();

        if (debug) LogUtils.debug("[{}] spawning grave", player.getName());
        if (disabledWorlds.contains(player.getWorld().getName())) {
            if (debug) LogUtils.debug("[{}] return: disabled world {}", player.getName(), player.getWorld().getName());
            return;
        }

        if (!player.hasPermission("axgraves.allowgraves")) {
            if (debug) LogUtils.debug("[{}] return: missing permission axgraves.allowgraves", player.getName());
            return;
        }

        if (player.getLastDamageCause() != null && blacklistedDeathCauses.contains(player.getLastDamageCause().getCause().name())) {
            if (debug) LogUtils.debug("[{}] return: blacklisted death cause {}", player.getName(), player.getLastDamageCause().getCause().name());
            return;
        }

        Location location = player.getLocation();
        location.add(0, -0.5, 0);
        if (debug) LogUtils.debug("[{}] location moved to {}", player.getName(), location.toString());

        final GravePreSpawnEvent gravePreSpawnEvent = new GravePreSpawnEvent(player, location);
        Bukkit.getPluginManager().callEvent(gravePreSpawnEvent);
        if (gravePreSpawnEvent.isCancelled()) {
            if (debug) LogUtils.debug("[{}] return: GravePreSpawnEvent cancelled", player.getName());
            return;
        }

        relocateIfUnsafe(location, player, debug);

        // captured *before* anything below clears the player's inventory, so `grave-item-order`
        // still has real armor/hand/offhand slots to prioritize even when override-keep-inventory
        // wipes the live inventory a few lines down.
        InventoryOrderSnapshot orderSnapshot = InventoryOrderSnapshot.capture(player.getInventory());

        if (debug) {
            LogUtils.debug("[{}] storeItems: {} - getKeepInventory: {} - overrideKeepInventory: {}", player.getName(), storeItems, event.getKeepInventory(), overrideKeepInventory);
            LogUtils.debug("[{}] storeXP: {} - getKeepLevel: {} - overrideKeepLevel: {}", player.getName(), storeXP, event.getKeepLevel(), overrideKeepLevel);
        }

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

            if (store) {
                event.getDrops().clear();
            }
            if (debug) LogUtils.debug("[{}] store: {} - drops size: {}", player.getName(), store, drops.size());
        }

        int xp = 0;
        if (storeXP) {
            boolean store = !event.getKeepLevel() || overrideKeepLevel;

            if (store) {
                // read the XP *before* zeroing the player's level - the old code zeroed level
                // first and then computed getExp() off the already-zeroed level, which returns
                // at most ~7 xp instead of the player's real total for anyone at level > 0.
                xp = Math.round(ExperienceUtils.getExp(player) * xpKeepPercentage);
                event.setDroppedExp(0);

                if (event.getKeepLevel() && overrideKeepLevel) {
                    player.setLevel(0);
                    player.setExp(0f); // also reset the progress bar - setTotalExperience(0) alone leaves it partially filled
                    player.setTotalExperience(0);
                }
            }
            if (debug) LogUtils.debug("[{}] store: {} - xp: {}", player.getName(), store, xp);
        }

        if (drops.isEmpty() && xp == 0) {
            if (debug) LogUtils.debug("[{}] return: drops empty and xp is 0", player.getName());
            return;
        }

        Grave grave;
        try {
            grave = new Grave(location, player, drops, xp, System.currentTimeMillis(), orderSnapshot);
            SpawnedGraves.addGrave(grave);
            // don't wait for the next periodic flush (up to storage.flush-interval-seconds away)
            // or the grave's own first tick to persist this - a crash in that window would lose
            // the grave, and everything in it, with no trace in storage at all.
            SaveGraves.saveNow(grave);
        } catch (Exception ex) {
            // items/xp were already taken from the player above; if grave construction itself
            // throws (bad config, oversized inventory, ...) they must be handed back rather than
            // vanishing - this used to have no try/catch at all, so any exception here silently
            // wiped the player's entire inventory and xp.
            LogUtils.error("failed to create a grave for {} - restoring their items/xp instead of losing them", ex, player.getName());
            restoreOnFailure(player, drops, xp);
            return;
        }

        if (debug) LogUtils.debug("[{}] created and added grave", player.getName());

        final GraveSpawnEvent graveSpawnEvent = new GraveSpawnEvent(player, grave);
        Bukkit.getPluginManager().callEvent(graveSpawnEvent);
    }

    private static void restoreOnFailure(Player player, List<ItemStack> drops, int xp) {
        for (ItemStack it : drops) {
            if (it == null || it.getType().isAir()) continue;
            for (ItemStack extra : player.getInventory().addItem(it).values()) {
                player.getWorld().dropItem(player.getLocation(), extra);
            }
        }
        if (xp > 0) {
            ExperienceUtils.changeExp(player, xp);
        }
    }

    /** Feature: relocates the grave if the death spot is void/lava/embedded/above the nether roof. See {@link SafeLocationFinder}. */
    private static void relocateIfUnsafe(Location location, Player player, boolean debug) {
        if (!CONFIG.getBoolean("safe-placement.enabled", true)) return;

        World world = location.getWorld();
        if (world == null) return;

        PlacementSettings settings = buildPlacementSettings(world);
        BlockProbe probe = new BukkitBlockProbe(world);

        // `location` has already been shifted down 0.5 blocks (see the caller) so that it
        // anchors to the ground block the player was standing ON, not the air block they were
        // standing IN - that's intentional, it's what makes the grave sit flush with the floor.
        // The space that actually needs a safety check is one block *above* that anchor.
        int standingY = location.getBlockY() + 1;
        SafeLocationFinder.Result result = SafeLocationFinder.find(probe, location.getBlockX(), standingY, location.getBlockZ(), settings);

        boolean usedSpawnFallback = false;
        if (!result.found()) {
            // the local search found nothing at all within its budget - this is what a genuine
            // void death looks like (an empty column has no solid block anywhere nearby, so
            // there's nothing to stand on), as opposed to "already fine" or "found something
            // nearby". Search around the world's spawn instead of leaving the grave floating in
            // open space - spawn terrain is essentially guaranteed to be loaded and solid.
            Location spawn = world.getSpawnLocation();
            SafeLocationFinder.Result fallback = SafeLocationFinder.find(probe, spawn.getBlockX(), spawn.getBlockY() + 1, spawn.getBlockZ(), settings);
            if (!fallback.found()) return; // nothing safe found anywhere reasonable - leave the grave where it died
            result = fallback;
            usedSpawnFallback = true;
        }

        if (!result.relocated() && !usedSpawnFallback) return; // original spot was already fine

        location.setX(result.x() + 0.5);
        location.setY(result.y() - 1);
        location.setZ(result.z() + 0.5);

        if (debug) {
            LogUtils.debug("[{}] grave relocated to {},{},{} ({} probes, spawnFallback={})",
                    player.getName(), result.x(), result.y(), result.z(), result.probes(), usedSpawnFallback);
        }

        if (settings.notifyOwner()) {
            String key = usedSpawnFallback ? "grave-relocated-far" : "grave-relocated";
            MESSAGEUTILS.sendLang(player, key, Map.of(
                    "%world%", LocationUtils.getWorldName(world),
                    "%x%", "" + result.x(),
                    "%y%", "" + result.y(),
                    "%z%", "" + result.z()));
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
