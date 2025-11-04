package com.artillexstudios.axgraves.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for finding safe locations for grave spawning
 * 
 * @author dei0 (dei2004)
 * @see <a href="https://github.com/dei2004">GitHub Profile</a>
 */
public class SafeLocationUtils {
    
    private static final Set<Material> UNSAFE_MATERIALS = new HashSet<>();
    private static final Set<Material> PASSABLE_MATERIALS = new HashSet<>();
    
    static {
        // Dangerous materials that graves should avoid
        UNSAFE_MATERIALS.add(Material.LAVA);
        UNSAFE_MATERIALS.add(Material.FIRE);
        UNSAFE_MATERIALS.add(Material.SOUL_FIRE);
        UNSAFE_MATERIALS.add(Material.CAMPFIRE);
        UNSAFE_MATERIALS.add(Material.SOUL_CAMPFIRE);
        UNSAFE_MATERIALS.add(Material.MAGMA_BLOCK);
        UNSAFE_MATERIALS.add(Material.SWEET_BERRY_BUSH);
        UNSAFE_MATERIALS.add(Material.CACTUS);
        UNSAFE_MATERIALS.add(Material.POWDER_SNOW);
        UNSAFE_MATERIALS.add(Material.WITHER_ROSE);
        
        // Passable materials (air, grass, etc.)
        PASSABLE_MATERIALS.add(Material.AIR);
        PASSABLE_MATERIALS.add(Material.CAVE_AIR);
        PASSABLE_MATERIALS.add(Material.VOID_AIR);
        PASSABLE_MATERIALS.add(Material.GRASS);
        PASSABLE_MATERIALS.add(Material.TALL_GRASS);
        PASSABLE_MATERIALS.add(Material.FERN);
        PASSABLE_MATERIALS.add(Material.LARGE_FERN);
        PASSABLE_MATERIALS.add(Material.DEAD_BUSH);
        PASSABLE_MATERIALS.add(Material.SEAGRASS);
        PASSABLE_MATERIALS.add(Material.TALL_SEAGRASS);
        PASSABLE_MATERIALS.add(Material.WATER);
        PASSABLE_MATERIALS.add(Material.SNOW);
        PASSABLE_MATERIALS.add(Material.VINE);
        PASSABLE_MATERIALS.add(Material.KELP);
        PASSABLE_MATERIALS.add(Material.KELP_PLANT);
        PASSABLE_MATERIALS.add(Material.WHEAT);
        PASSABLE_MATERIALS.add(Material.CARROTS);
        PASSABLE_MATERIALS.add(Material.POTATOES);
        PASSABLE_MATERIALS.add(Material.BEETROOTS);
        PASSABLE_MATERIALS.add(Material.TORCH);
        PASSABLE_MATERIALS.add(Material.WALL_TORCH);
        PASSABLE_MATERIALS.add(Material.REDSTONE_TORCH);
        PASSABLE_MATERIALS.add(Material.REDSTONE_WALL_TORCH);
        PASSABLE_MATERIALS.add(Material.SOUL_TORCH);
        PASSABLE_MATERIALS.add(Material.SOUL_WALL_TORCH);
        PASSABLE_MATERIALS.add(Material.LEVER);
        PASSABLE_MATERIALS.add(Material.REDSTONE_WIRE);
        PASSABLE_MATERIALS.add(Material.RAIL);
        PASSABLE_MATERIALS.add(Material.POWERED_RAIL);
        PASSABLE_MATERIALS.add(Material.DETECTOR_RAIL);
        PASSABLE_MATERIALS.add(Material.ACTIVATOR_RAIL);
    }
    
    /**
     * Checks if a location is safe for grave spawning
     * A safe location must have:
     * - Solid ground below
     * - 2 blocks of air/passable space above for player
     * - No dangerous materials nearby
     * - Not in void (must have solid blocks within 10 blocks below)
     * 
     * @param location The location to check
     * @return true if the location is safe, false otherwise
     */
    public static boolean isSafeLocation(@NotNull Location location) {
        Block block = location.getBlock();
        Block below = block.getRelative(BlockFace.DOWN);
        Block above = block.getRelative(BlockFace.UP);
        Block twoAbove = above.getRelative(BlockFace.UP);
        
        // Check if in void (below minimum world height)
        if (location.getY() < location.getWorld().getMinHeight() + 5) {
            return false;
        }
        
        // Check if at or above max height (need space for player)
        if (location.getY() >= location.getWorld().getMaxHeight() - 2) {
            return false;
        }
        
        // Current block and 2 blocks above MUST be passable (air/grass/water)
        // This ensures player can stand here
        if (!isPassableMaterial(block.getType())) {
            return false;
        }
        if (!isPassableMaterial(above.getType())) {
            return false;
        }
        if (!isPassableMaterial(twoAbove.getType())) {
            return false;
        }
        
        // Check if the blocks are dangerous
        if (isUnsafeMaterial(block.getType()) || isUnsafeMaterial(above.getType()) || isUnsafeMaterial(twoAbove.getType())) {
            return false;
        }
        
        // Block below MUST be solid and safe
        if (!below.getType().isSolid()) {
            return false;
        }
        
        // Block below must not be dangerous
        if (isUnsafeMaterial(below.getType())) {
            return false;
        }
        
        // Check if we're floating in air (void check) - enhanced void detection
        if (below.getType() == Material.AIR || below.getType() == Material.CAVE_AIR || below.getType() == Material.VOID_AIR) {
            return false;
        }
        
        // Enhanced void check - make sure there's solid ground within 10 blocks below
        boolean hasSolidGroundBelow = false;
        for (int i = 1; i <= 10; i++) {
            Block checkBelow = location.clone().subtract(0, i, 0).getBlock();
            if (checkBelow.getType().isSolid() && !isUnsafeMaterial(checkBelow.getType())) {
                hasSolidGroundBelow = true;
                break;
            }
        }
        
        if (!hasSolidGroundBelow) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if a material is passable (player can walk through it)
     * 
     * @param material The material to check
     * @return true if passable, false otherwise
     */
    private static boolean isPassableMaterial(@NotNull Material material) {
        return PASSABLE_MATERIALS.contains(material) || !material.isSolid();
    }
    
    /**
     * Checks if a material is unsafe for grave spawning
     * 
     * @param material The material to check
     * @return true if unsafe, false otherwise
     */
    private static boolean isUnsafeMaterial(@NotNull Material material) {
        return UNSAFE_MATERIALS.contains(material);
    }
    
    /**
     * Finds a safe location near the death location
     * Searches in a spiral pattern outward from the death location
     * Priority order: Same Y level → Above → Below → Further away
     * 
     * @param deathLocation The original death location
     * @param maxRadius Maximum search radius in blocks
     * @param maxVerticalSearch Maximum vertical search distance
     * @return A safe location, or the original location if no safe spot found
     */
    @NotNull
    public static Location findSafeLocation(@NotNull Location deathLocation, int maxRadius, int maxVerticalSearch) {
        // First check if current location is already safe
        if (isSafeLocation(deathLocation)) {
            return deathLocation.clone();
        }
        
        // Priority 1: Search same Y level first (horizontal only)
        for (int radius = 1; radius <= maxRadius; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    // Only check the perimeter of each square
                    if (Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    
                    Location checkLocation = deathLocation.clone().add(x, 0, z);
                    
                    // Skip if out of world bounds
                    if (checkLocation.getY() < checkLocation.getWorld().getMinHeight() || 
                        checkLocation.getY() > checkLocation.getWorld().getMaxHeight() - 2) {
                        continue;
                    }
                    
                    if (isSafeLocation(checkLocation)) {
                        return checkLocation;
                    }
                }
            }
        }
        
        // Priority 2: Search above first (players usually want to go up)
        for (int radius = 1; radius <= maxRadius; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    
                    // Search upward first
                    for (int y = 1; y <= maxVerticalSearch; y++) {
                        Location checkLocation = deathLocation.clone().add(x, y, z);
                        
                        if (checkLocation.getY() >= checkLocation.getWorld().getMaxHeight() - 2) {
                            break;
                        }
                        
                        if (isSafeLocation(checkLocation)) {
                            return checkLocation;
                        }
                    }
                }
            }
        }
        
        // Priority 3: Search below
        for (int radius = 1; radius <= maxRadius; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    
                    // Search downward
                    for (int y = -1; y >= -maxVerticalSearch; y--) {
                        Location checkLocation = deathLocation.clone().add(x, y, z);
                        
                        if (checkLocation.getY() < checkLocation.getWorld().getMinHeight()) {
                            break;
                        }
                        
                        if (isSafeLocation(checkLocation)) {
                            return checkLocation;
                        }
                    }
                }
            }
        }
        
        // Priority 4: Find nearest surface above (emergency)
        Location surfaceCheck = deathLocation.clone();
        for (int y = 0; y < 50; y++) {
            surfaceCheck.add(0, 1, 0);
            if (surfaceCheck.getY() >= surfaceCheck.getWorld().getMaxHeight() - 2) {
                break;
            }
            
            if (isSafeLocation(surfaceCheck)) {
                return surfaceCheck;
            }
        }
        
        // Last resort: Try to find ANY solid ground nearby
        for (int y = deathLocation.getBlockY(); y >= deathLocation.getWorld().getMinHeight(); y--) {
            Location groundCheck = deathLocation.clone();
            groundCheck.setY(y);
            
            if (groundCheck.getBlock().getType().isSolid() && 
                !isUnsafeMaterial(groundCheck.getBlock().getType())) {
                // Found solid ground, check if we can spawn above it
                Location spawnLocation = groundCheck.clone().add(0, 1, 0);
                if (isSafeLocation(spawnLocation)) {
                    return spawnLocation;
                }
            }
        }
        
        // Absolute last resort: return original location
        return deathLocation.clone();
    }
    
    /**
     * Finds a safe location with maximum search parameters
     * Searches up to 100 blocks horizontally and 100 blocks vertically
     * For The End dimension, searches up to 500 blocks vertically
     * 
     * @param deathLocation The original death location
     * @return A safe location
     */
    @NotNull
    public static Location findSafeLocation(@NotNull Location deathLocation) {
        // Use extra vertical search in The End due to void islands
        boolean isEnd = deathLocation.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END;
        int verticalSearch = isEnd ? 500 : 100;
        
        org.bukkit.Bukkit.getLogger().info("[AxGraves-dei0] Searching for safe location. Dimension: " + deathLocation.getWorld().getEnvironment() + ", Vertical search: " + verticalSearch);
        
        Location safeLoc = findSafeLocation(deathLocation, 100, verticalSearch);
        
        org.bukkit.Bukkit.getLogger().info("[AxGraves-dei0] Safe location found at: " + safeLoc.getBlockX() + ", " + safeLoc.getBlockY() + ", " + safeLoc.getBlockZ());
        
        return safeLoc;
    }
}
