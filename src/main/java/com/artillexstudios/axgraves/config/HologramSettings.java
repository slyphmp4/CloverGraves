package com.artillexstudios.axgraves.config;

import com.artillexstudios.axapi.libs.boostedyaml.block.implementation.Section;
import com.artillexstudios.axapi.packetentity.meta.entity.DisplayMeta;
import com.artillexstudios.axapi.packetentity.meta.entity.TextDisplayMeta;
import com.artillexstudios.axapi.utils.logging.LogUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Validated, pre-parsed {@code holograms.*} config. Grave used to parse these values directly
 * inline every time a hologram was (re)built, with no validation:
 * {@code Integer.parseInt(section.getString("background-color"), 16)} overflows
 * {@code Integer.MAX_VALUE} for any opaque AARRGGBB color (the config file's own example,
 * "00000000", is fine, but anything with a non-zero alpha byte like "FF000000" is not), and
 * {@code Alignment.valueOf}/{@code BillboardConstrain.valueOf} threw on any typo. Because
 * hologram construction happens inside the death path with no surrounding try/catch, any of
 * these threw straight through {@code new Grave(...)} and wiped the dying player's inventory.
 */
public record HologramSettings(boolean seeThrough, boolean shadow, TextDisplayMeta.Alignment alignment,
                                int backgroundColor, DisplayMeta.BillboardConstrain billboard) {

    private static final int DEFAULT_BACKGROUND = 0x00000000;
    private static final TextDisplayMeta.Alignment DEFAULT_ALIGNMENT = TextDisplayMeta.Alignment.CENTER;
    private static final DisplayMeta.BillboardConstrain DEFAULT_BILLBOARD = DisplayMeta.BillboardConstrain.VERTICAL;

    @NotNull
    public static HologramSettings parse(@Nullable Section section) {
        if (section == null) {
            return new HologramSettings(false, true, DEFAULT_ALIGNMENT, DEFAULT_BACKGROUND, DEFAULT_BILLBOARD);
        }

        boolean seeThrough = section.getBoolean("see-through", false);
        boolean shadow = section.getBoolean("shadow", true);
        TextDisplayMeta.Alignment alignment = parseAlignment(section.getString("alignment"));
        DisplayMeta.BillboardConstrain billboard = parseBillboard(section.getString("billboard"));
        int backgroundColor = parseColor(section.getString("background-color"));

        return new HologramSettings(seeThrough, shadow, alignment, backgroundColor, billboard);
    }

    /**
     * Parses an AARRGGBB hex string as an unsigned 32-bit int. {@code Integer.parseInt(s, 16)}
     * throws for any value at or above {@code 0x80000000} (i.e. any opaque color) because it
     * parses as a signed int; {@code parseUnsignedInt} handles the full range correctly.
     */
    static int parseColor(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_BACKGROUND;
        try {
            return Integer.parseUnsignedInt(raw.trim(), 16);
        } catch (NumberFormatException ex) {
            LogUtils.warn("invalid holograms.background-color '{}', expected an 8-digit AARRGGBB hex value, using default", raw);
            return DEFAULT_BACKGROUND;
        }
    }

    static TextDisplayMeta.Alignment parseAlignment(@Nullable String raw) {
        if (raw == null) return DEFAULT_ALIGNMENT;
        try {
            return TextDisplayMeta.Alignment.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            LogUtils.warn("invalid holograms.alignment '{}', using default", raw);
            return DEFAULT_ALIGNMENT;
        }
    }

    static DisplayMeta.BillboardConstrain parseBillboard(@Nullable String raw) {
        if (raw == null) return DEFAULT_BILLBOARD;
        try {
            return DisplayMeta.BillboardConstrain.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            LogUtils.warn("invalid holograms.billboard '{}', using default", raw);
            return DEFAULT_BILLBOARD;
        }
    }
}
