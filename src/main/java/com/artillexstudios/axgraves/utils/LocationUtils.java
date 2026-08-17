package com.artillexstudios.axgraves.utils;

import com.artillexstudios.axapi.libs.boostedyaml.block.implementation.Section;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;

public class LocationUtils {

    public record HeightLimits(double min, double max) {
    }

    @NotNull
    public static Location getCenterOf(@NotNull Location location, boolean keepYaw, boolean keepPitch) {
        Location loc = location.getBlock().getLocation().add(0.5, 0.5, 0.5);
        if (keepYaw) loc.setYaw(location.getYaw());
        if (keepPitch) loc.setPitch(location.getPitch());
        return loc;
    }

    public static int getNearestDirection(float x) {
        return Math.round(x / 90f) * 90;
    }

    /** Configured (or dimension-default) vertical bounds for {@code world}, shared by {@link #clampLocation} and grave safe-placement. */
    @NotNull
    public static HeightLimits getHeightLimits(@NotNull World world) {
        Section section = CONFIG.getSection("spawn-height-limits." + world.getName());
        if (section != null) {
            return new HeightLimits(section.getDouble("min"), section.getDouble("max"));
        }

        return switch (world.getEnvironment()) {
            case NETHER, THE_END -> new HeightLimits(0, 255);
            default -> new HeightLimits(-64, 319);
        };
    }

    public static void clampLocation(@NotNull Location location) {
        HeightLimits limits = getHeightLimits(location.getWorld());
        location.setY(Math.clamp(location.getY(), limits.min(), limits.max()));
    }

    @NotNull
    public static String getWorldName(World world) {
        if (world == null) return "---";
        return CONFIG.getString("world-name." + world.getName(), world.getName());
    }
}
