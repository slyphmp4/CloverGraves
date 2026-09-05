package com.slyph.clovergraves.grave.hologram;

import com.slyph.clovergraves.config.HologramSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class TextDisplayGraveHologram implements GraveHologram {
    private static final int LINE_WIDTH = 1000;

    private final Location displayLocation;
    private final HologramSettings settings;
    private final boolean replaceOnTextUpdate;
    private TextDisplay display;
    private Component currentText;

    public TextDisplayGraveHologram(@NotNull Location topLocation, @NotNull List<Component> lines,
                                    @NotNull HologramSettings settings, float lineSpacing) {
        displayLocation = topLocation.clone().add(0, -lineSpacing * Math.max(0, lines.size() - 1) + 0.25, 0);
        this.settings = settings;
        replaceOnTextUpdate = isCardboard();
        currentText = joinLines(lines);
        display = spawnDisplay(currentText);
    }

    @Override
    public void setLines(@NotNull List<Component> lines) {
        Component nextText = joinLines(lines);
        if (!isValid()) {
            currentText = nextText;
            display = spawnDisplay(nextText);
            return;
        }
        if (nextText.equals(currentText)) return;

        if (replaceOnTextUpdate) {
            TextDisplay previous = display;
            previous.remove();
            display = spawnDisplay(nextText);
        } else {
            display.text(nextText);
        }
        currentText = nextText;
    }

    @NotNull
    public TextDisplay display() {
        return display;
    }

    @Override
    public boolean isValid() {
        return display != null && display.isValid() && !display.isDead();
    }

    @Override
    public void remove() {
        if (display != null) display.remove();
    }

    @NotNull
    private TextDisplay spawnDisplay(@NotNull Component text) {
        World world = Objects.requireNonNull(displayLocation.getWorld(), "display world");
        TextDisplay created = world.spawn(displayLocation, TextDisplay.class);
        created.setPersistent(false);
        created.setInterpolationDelay(0);
        created.setInterpolationDuration(0);
        created.setTeleportDuration(0);
        created.setSeeThrough(settings.seeThrough());
        created.setShadowed(settings.shadow());
        created.setDefaultBackground(false);
        created.setAlignment(settings.alignment());
        created.setBackgroundColor(Color.fromARGB(settings.backgroundColor()));
        created.setLineWidth(LINE_WIDTH);
        created.setBillboard(settings.billboard());
        created.text(text);
        return created;
    }

    private static boolean isCardboard() {
        String server = (Bukkit.getName() + " " + Bukkit.getVersion()).toLowerCase(Locale.ROOT);
        return server.contains("cardboard");
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
