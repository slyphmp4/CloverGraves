package com.slyph.clovergraves.config;

import com.slyph.clovergraves.utils.CloverLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record HologramSettings(boolean seeThrough, boolean shadow, TextDisplay.TextAlignment alignment,
                               int backgroundColor, Display.Billboard billboard) {
    private static final int DEFAULT_BACKGROUND = 0x00000000;
    private static final TextDisplay.TextAlignment DEFAULT_ALIGNMENT = TextDisplay.TextAlignment.CENTER;
    private static final Display.Billboard DEFAULT_BILLBOARD = Display.Billboard.VERTICAL;

    @NotNull
    public static HologramSettings parse(@Nullable ConfigurationSection section) {
        if (section == null) {
            return new HologramSettings(false, true, DEFAULT_ALIGNMENT, DEFAULT_BACKGROUND, DEFAULT_BILLBOARD);
        }

        return new HologramSettings(
                section.getBoolean("see-through", false),
                section.getBoolean("shadow", true),
                parseAlignment(section.getString("alignment")),
                parseColor(section.getString("background-color")),
                parseBillboard(section.getString("billboard"))
        );
    }

    static int parseColor(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_BACKGROUND;
        try {
            return Integer.parseUnsignedInt(raw.trim(), 16);
        } catch (NumberFormatException ex) {
            CloverLogger.warn("invalid holograms.background-color '{}', using default", raw);
            return DEFAULT_BACKGROUND;
        }
    }

    static TextDisplay.TextAlignment parseAlignment(@Nullable String raw) {
        if (raw == null) return DEFAULT_ALIGNMENT;
        try {
            return TextDisplay.TextAlignment.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            CloverLogger.warn("invalid holograms.alignment '{}', using default", raw);
            return DEFAULT_ALIGNMENT;
        }
    }

    static Display.Billboard parseBillboard(@Nullable String raw) {
        if (raw == null) return DEFAULT_BILLBOARD;
        try {
            return Display.Billboard.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            CloverLogger.warn("invalid holograms.billboard '{}', using default", raw);
            return DEFAULT_BILLBOARD;
        }
    }
}
