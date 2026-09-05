package com.slyph.clovergraves.config;

import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HologramSettingsTest {

    @Test
    void opaqueBackgroundColorDoesNotThrow() {
        assertDoesNotThrow(() -> HologramSettings.parseColor("FF000000"));
        assertEquals(0xFF000000, HologramSettings.parseColor("FF000000"));
    }

    @Test
    void transparentBackgroundColorParsesToZero() {
        assertEquals(0, HologramSettings.parseColor("00000000"));
    }

    @Test
    void invalidBackgroundColorFallsBackToDefault() {
        assertDoesNotThrow(() -> HologramSettings.parseColor("not-a-color"));
        assertEquals(0, HologramSettings.parseColor("not-a-color"));
    }

    @Test
    void nullOrBlankBackgroundColorFallsBackToDefault() {
        assertEquals(0, HologramSettings.parseColor(null));
        assertEquals(0, HologramSettings.parseColor("  "));
    }

    @Test
    void validAlignmentIsCaseInsensitive() {
        assertEquals(TextDisplay.TextAlignment.LEFT, HologramSettings.parseAlignment("left"));
        assertEquals(TextDisplay.TextAlignment.CENTER, HologramSettings.parseAlignment("CENTER"));
    }

    @Test
    void invalidAlignmentFallsBackToDefaultInsteadOfThrowing() {
        assertDoesNotThrow(() -> HologramSettings.parseAlignment("sideways"));
        assertEquals(TextDisplay.TextAlignment.CENTER, HologramSettings.parseAlignment("sideways"));
    }

    @Test
    void validBillboardIsCaseInsensitive() {
        assertEquals(Display.Billboard.VERTICAL, HologramSettings.parseBillboard("vertical"));
    }

    @Test
    void invalidBillboardFallsBackToDefaultInsteadOfThrowing() {
        assertDoesNotThrow(() -> HologramSettings.parseBillboard("nonsense"));
        assertEquals(Display.Billboard.VERTICAL, HologramSettings.parseBillboard("nonsense"));
    }
}
