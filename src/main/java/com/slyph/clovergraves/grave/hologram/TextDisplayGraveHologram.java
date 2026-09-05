package com.slyph.clovergraves.grave.hologram;

import com.slyph.clovergraves.config.HologramSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class TextDisplayGraveHologram implements GraveHologram {
    private static final int LINE_WIDTH = 1000;

    private final TextDisplay display;

    public TextDisplayGraveHologram(@NotNull Location topLocation, @NotNull List<Component> lines,
                                    @NotNull HologramSettings settings, float lineSpacing) {
        Location displayLocation = topLocation.clone().add(0, -lineSpacing * Math.max(0, lines.size() - 1) + 0.25, 0);
        display = topLocation.getWorld().spawn(displayLocation, TextDisplay.class);
        display.setPersistent(false);
        display.setSeeThrough(settings.seeThrough());
        display.setShadowed(settings.shadow());
        display.setDefaultBackground(false);
        display.setAlignment(settings.alignment());
        display.setBackgroundColor(Color.fromARGB(settings.backgroundColor()));
        display.setLineWidth(LINE_WIDTH);
        display.setBillboard(settings.billboard());
        setLines(lines);
    }

    @Override
    public void setLines(@NotNull List<Component> lines) {
        if (!isValid()) return;
        display.text(joinLines(lines));
    }

    @NotNull
    public TextDisplay display() {
        return display;
    }

    @Override
    public boolean isValid() {
        return display.isValid() && !display.isDead();
    }

    @Override
    public void remove() {
        if (!display.isDead()) display.remove();
    }

    @NotNull
    private static Component joinLines(@NotNull List<Component> lines) {
        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) result = result.append(Component.newline());
            result = result.append(lines.get(i));
        }
        return result;
    }
}
