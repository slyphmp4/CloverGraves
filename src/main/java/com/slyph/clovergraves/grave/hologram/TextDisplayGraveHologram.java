package com.slyph.clovergraves.grave.hologram;

import com.slyph.clovergraves.config.HologramSettings;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class TextDisplayGraveHologram implements GraveHologram {
    private static final int LINE_WIDTH = 1000;

    private final TextDisplay display;

    public TextDisplayGraveHologram(@NotNull Location topLocation, @NotNull List<String> lines,
                                    @NotNull HologramSettings settings, float lineSpacing) {
        Location displayLocation = topLocation.clone().add(0, -lineSpacing * Math.max(0, lines.size() - 1) + 0.25, 0);
        display = (TextDisplay) topLocation.getWorld().spawnEntity(displayLocation, EntityType.TEXT_DISPLAY);
        display.setPersistent(false);
        display.setSeeThrough(settings.seeThrough());
        display.setShadowed(settings.shadow());
        display.setAlignment(settings.alignment());
        display.setBackgroundColor(Color.fromARGB(settings.backgroundColor()));
        display.setLineWidth(LINE_WIDTH);
        display.setBillboard(settings.billboard());
        setLines(lines);
    }

    @Override
    public void setLines(@NotNull List<String> lines) {
        if (!display.isValid()) return;
        display.setText(String.join("\n", lines));
    }

    @Override
    public boolean isValid() {
        return display.isValid();
    }

    @Override
    public void remove() {
        if (display.isValid()) display.remove();
    }
}
