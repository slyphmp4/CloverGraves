package com.artillexstudios.axgraves.commands.subcommands;

import com.artillexstudios.axapi.utils.PaperUtils;
import com.artillexstudios.axgraves.AxGraves;
import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;
import static com.artillexstudios.axgraves.AxGraves.MESSAGEUTILS;

/**
 * Teleport command with cooldown system
 * Made by dei0 (dei2004) - https://github.com/dei2004
 */
public enum Teleport {
    INSTANCE;

    // Store cooldowns and tasks for each player
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> countdownTasks = new HashMap<>();

    public void execute(Player sender, World world, Double x, Double y, Double z) {
        // Get cooldown setting
        long cooldownSeconds = CONFIG.getLong("teleport-cooldown", 5L);
        
        // Check if player is on cooldown (only if they don't have bypass permission)
        if (!sender.hasPermission("axgraves.tp.bypass.cooldown")) {
            long currentTime = System.currentTimeMillis();
            
            if (cooldowns.containsKey(sender.getUniqueId())) {
                long timeLeft = cooldowns.get(sender.getUniqueId()) - currentTime;
                if (timeLeft > 0) {
                    long secondsLeft = (timeLeft / 1000) + 1;
                    MESSAGEUTILS.sendLang(sender, "teleport.cooldown", Map.of("%time%", String.valueOf(secondsLeft)));
                    return;
                }
            }
        }

        // Determine teleport location
        Location targetLocation = null;
        
        if (world == null || x == null || y == null || z == null) {
            Grave grave = SpawnedGraves.getGraves().stream().filter(gr -> gr.getPlayer().getUniqueId().equals(sender.getUniqueId())).findAny().orElse(null);
            if (grave == null) {
                MESSAGEUTILS.sendLang(sender, "grave-list.no-graves");
                return;
            }
            targetLocation = grave.getLocation();
        } else {
            final Location location = new Location(world, x, y, z);
            Optional<Grave> grave = SpawnedGraves.getGraves().stream()
                    .filter(gr -> gr.getPlayer().getUniqueId().equals(sender.getUniqueId()))
                    .filter(gr -> Objects.equals(gr.getLocation().getWorld(), location.getWorld()))
                    .filter(gr -> gr.getLocation().distanceSquared(location) < 1)
                    .findAny();

            if (!sender.hasPermission("axgraves.tp.bypass") && grave.isEmpty()) return;
            targetLocation = grave.isEmpty() ? location : grave.get().getLocation();
        }

        // Start teleport countdown with delay
        final Location finalTarget = targetLocation;
        if (!sender.hasPermission("axgraves.tp.bypass.cooldown")) {
            startTeleportCountdown(sender, finalTarget, (int) cooldownSeconds);
            
            // Set cooldown for next use
            long currentTime = System.currentTimeMillis();
            cooldowns.put(sender.getUniqueId(), currentTime + (cooldownSeconds * 1000));
        } else {
            // Instant teleport for players with bypass
            PaperUtils.teleportAsync(sender, finalTarget);
        }
    }

    /**
     * Starts a teleport countdown with delay before actually teleporting
     * @param player The player to teleport
     * @param targetLocation The location to teleport to
     * @param seconds The number of seconds to wait before teleporting
     */
    private void startTeleportCountdown(Player player, Location targetLocation, int seconds) {
        // Cancel any existing countdown for this player
        if (countdownTasks.containsKey(player.getUniqueId())) {
            countdownTasks.get(player.getUniqueId()).cancel();
        }

        // Store initial location to detect movement
        final Location startLocation = player.getLocation().clone();

        // Create countdown task
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(AxGraves.getInstance(), new Runnable() {
            int timeLeft = seconds;

            @Override
            public void run() {
                // Cancel if player logged out
                if (!player.isOnline()) {
                    BukkitTask task = countdownTasks.remove(player.getUniqueId());
                    if (task != null) {
                        task.cancel();
                    }
                    return;
                }

                // Cancel if player moved
                if (player.getLocation().distanceSquared(startLocation) > 1.0) {
                    player.sendTitle(ChatColor.RED + "✘ Cancelled", ChatColor.GRAY + "You moved!", 5, 40, 10);
                    MESSAGEUTILS.sendLang(player, "teleport.cancelled");
                    BukkitTask task = countdownTasks.remove(player.getUniqueId());
                    if (task != null) {
                        task.cancel();
                    }
                    return;
                }

                // Teleport when countdown reaches 0
                if (timeLeft <= 0) {
                    PaperUtils.teleportAsync(player, targetLocation);
                    BukkitTask task = countdownTasks.remove(player.getUniqueId());
                    if (task != null) {
                        task.cancel();
                    }
                    return;
                }

                // Display countdown as title
                String message = CONFIG.getString("teleport-countdown-display", "&#00FF00✈ Teleporting in &#FFFFFF%time%s")
                        .replace("%time%", String.valueOf(timeLeft));
                
                // Convert hex colors and standard color codes
                message = Teleport.translateHexColorCodes(message);
                message = ChatColor.translateAlternateColorCodes('&', message);
                
                // Send as title (title, subtitle, fadeIn, stay, fadeOut in ticks)
                player.sendTitle(message, "", 0, 25, 5);

                timeLeft--;
            }
        }, 0L, 20L); // Run every second (20 ticks)

        countdownTasks.put(player.getUniqueId(), task);
    }

    /**
     * Translates hex color codes in the format &#RRGGBB to Minecraft's format
     * @param message The message with hex color codes
     * @return The message with translated hex colors
     */
    private static String translateHexColorCodes(String message) {
        final java.util.regex.Pattern hexPattern = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})");
        java.util.regex.Matcher matcher = hexPattern.matcher(message);
        StringBuilder buffer = new StringBuilder(message.length() + 4 * 8);
        
        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, "§x"
                    + "§" + group.charAt(0) + "§" + group.charAt(1)
                    + "§" + group.charAt(2) + "§" + group.charAt(3)
                    + "§" + group.charAt(4) + "§" + group.charAt(5));
        }
        
        return matcher.appendTail(buffer).toString();
    }

    /**
     * Clears cooldown for a player (useful for reloads or admin commands)
     * @param playerId The UUID of the player
     */
    public void clearCooldown(UUID playerId) {
        cooldowns.remove(playerId);
        BukkitTask task = countdownTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Clears all cooldowns (useful for plugin reload)
     */
    public void clearAllCooldowns() {
        cooldowns.clear();
        for (BukkitTask task : countdownTasks.values()) {
            task.cancel();
        }
        countdownTasks.clear();
    }
}
