package com.slyph.clovergraves.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LocationCodec {
    private LocationCodec() {
    }

    @NotNull
    public static String serialize(@NotNull Location location) {
        World world = location.getWorld();
        if (world == null) throw new IllegalArgumentException("Location has no world");
        return world.getName() + ';' + location.getX() + ';' + location.getY() + ';' + location.getZ() + ';' + location.getYaw() + ';' + location.getPitch();
    }

    @Nullable
    public static Location deserialize(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        String separator = value.indexOf(';') >= 0 ? ";" : ",";
        String[] parts = value.split(separator, -1);
        if (parts.length != 6) return null;

        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            return new Location(
                    world,
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]),
                    Float.parseFloat(parts[5])
            );
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
