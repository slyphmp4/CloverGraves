package com.slyph.clovergraves.grave.hologram;

import com.slyph.clovergraves.config.HologramSettings;
import com.slyph.clovergraves.utils.CloverLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GraveHologramFactory {
    private static final AtomicBoolean FALLBACK_LOGGED = new AtomicBoolean(false);

    private GraveHologramFactory() {
    }

    @NotNull
    public static GraveHologram create(@NotNull Location topLocation, @NotNull List<String> lines,
                                       @NotNull HologramSettings settings, float lineSpacing) {
        if (isCardboard()) return armorStand(topLocation, lines, lineSpacing);

        try {
            return new TextDisplayGraveHologram(topLocation, lines, settings, lineSpacing);
        } catch (Throwable throwable) {
            if (FALLBACK_LOGGED.compareAndSet(false, true)) {
                CloverLogger.warn("TextDisplay holograms are unavailable; using ArmorStand holograms instead: {}", throwable.getMessage());
            }
            return armorStand(topLocation, lines, lineSpacing);
        }
    }

    @NotNull
    private static GraveHologram armorStand(@NotNull Location topLocation, @NotNull List<String> lines, float lineSpacing) {
        if (FALLBACK_LOGGED.compareAndSet(false, true)) {
            CloverLogger.info("Cardboard-compatible ArmorStand hologram backend enabled");
        }
        return new ArmorStandGraveHologram(topLocation, lines, lineSpacing);
    }

    private static boolean isCardboard() {
        String server = (Bukkit.getName() + ' ' + Bukkit.getVersion()).toLowerCase(Locale.ROOT);
        return server.contains("cardboard");
    }
}
