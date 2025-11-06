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
     * - Current block is solid and safe (the grave will be placed ON this block)
     * - 2 blocks of air/passable space above for the grave and player
     * - No dangerous materials
     * - Not in void
     * 
     * @param location The location to check (this should be the SOLID BLOCK the grave sits on)
     * @return true if the location is safe, false otherwise
     */
    public static boolean isSafeLocation(@NotNull Location location) {
        Block block = location.getBlock();
        Block above = block.getRelative(BlockFace.UP);
        Block twoAbove = above.getRelative(BlockFace.UP);
        
        // Check if in void (below minimum world height)
        if (location.getY() < location.getWorld().getMinHeight()) {
            return false;
        }
        
        // Check if at or above max height (need space above for grave)
        if (location.getY() >= location.getWorld().getMaxHeight() - 2) {
            return false;
        }
        
        // Current block MUST be solid (this is what the grave sits ON)
        if (!block.getType().isSolid()) {
            return false;
        }
        
        // Current block must not be dangerous
        if (isUnsafeMaterial(block.getType())) {
            return false;
        }
        
        // 2 blocks above MUST be passable (space for grave and player)
        if (!isPassableMaterial(above.getType())) {
            return false;
        }
        if (!isPassableMaterial(twoAbove.getType())) {
            return false;
        }
        
        // Check if the blocks above are dangerous
        if (isUnsafeMaterial(above.getType()) || isUnsafeMaterial(twoAbove.getType())) {
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
     * Returns the SOLID BLOCK location (grave will spawn on top of this)
     * 
     * @param deathLocation The original death location
     * @param maxRadius Maximum search radius in blocks
     * @param maxVerticalSearch Maximum vertical search distance
     * @return A safe location (the solid block), or the best fallback location
     */
    @NotNull
    public static Location findSafeLocation(@NotNull Location deathLocation, int maxRadius, int maxVerticalSearch) {
        // First, find solid ground at or near the death location
        Location searchStart = deathLocation.clone();
        
        // If we're standing in air, first find the ground below us
        if (!searchStart.getBlock().getType().isSolid()) {
            for (int y = 0; y >= -50; y--) {
                Location groundCheck = deathLocation.clone().add(0, y, 0);
                if (groundCheck.getY() < groundCheck.getWorld().getMinHeight()) {
                    break;
                }
                
                if (groundCheck.getBlock().getType().isSolid() && !isUnsafeMaterial(groundCheck.getBlock().getType())) {
                    searchStart = groundCheck;
                    break;
                }
            }
        }
        
        // Check if current block is already safe
        if (isSafeLocation(searchStart)) {
            return searchStart.clone();
        }
        
        // Priority 1: Search same Y level first (horizontal only)
        for (int radius = 1; radius <= maxRadius; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    // Only check the perimeter of each square
                    if (Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    
                    Location checkLocation = searchStart.clone().add(x, 0, z);
                    
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
                        Location checkLocation = searchStart.clone().add(x, y, z);
                        
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
                        Location checkLocation = searchStart.clone().add(x, y, z);
                        
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
        
        // Emergency fallback: Find ANY solid ground nearby
        for (int radius = 1; radius <= Math.min(maxRadius, 50); radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int y = 10; y >= -10; y--) {
                        Location checkLocation = searchStart.clone().add(x, y, z);
                        
                        if (checkLocation.getY() < checkLocation.getWorld().getMinHeight() || 
                            checkLocation.getY() >= checkLocation.getWorld().getMaxHeight() - 2) {
                            continue;
                        }
                        
                        Block block = checkLocation.getBlock();
                        if (block.getType().isSolid() && !isUnsafeMaterial(block.getType())) {
                            // Found solid ground, check if we can spawn above it
                            if (isSafeLocation(checkLocation)) {
                                return checkLocation;
                            }
                        }
                    }
                }
            }
        }
        
        // Absolute last resort: Create a safe spot at the search start location
        // Return the block below the death location if it's solid, otherwise the death location
        if (searchStart.getBlock().getType().isSolid()) {
            return searchStart.clone();
        }
        
        // Try to find the nearest solid block below
        for (int y = 0; y >= -64; y--) {
            Location fallback = searchStart.clone().add(0, y, 0);
            if (fallback.getY() < fallback.getWorld().getMinHeight()) {
                break;
            }
            
            if (fallback.getBlock().getType().isSolid() && !isUnsafeMaterial(fallback.getBlock().getType())) {
                return fallback;
            }
        }
        
        // Very last resort: return search start
        return searchStart.clone();
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
