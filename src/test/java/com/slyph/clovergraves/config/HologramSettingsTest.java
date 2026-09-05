package com.slyph.clovergraves.config;

import com.artillexstudios.axapi.packetentity.meta.entity.DisplayMeta;
import com.artillexstudios.axapi.packetentity.meta.entity.TextDisplayMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Directly reproduces (and verifies the fix for) the crash the plugin shipped with:
 * {@code Integer.parseInt("FF000000", 16)} throws {@code NumberFormatException} for any opaque
 * AARRGGBB color, because it parses as a signed int and 0xFF000000 exceeds Integer.MAX_VALUE.
 * Since hologram construction happened inline in the death path with no surrounding try/catch,
 * this turned "set a fully opaque hologram background" into "every death wipes the player's
 * inventory".
 */
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
        assertEquals(TextDisplayMeta.Alignment.LEFT, HologramSettings.parseAlignment("left"));
        assertEquals(TextDisplayMeta.Alignment.CENTER, HologramSettings.parseAlignment("CENTER"));
    }

    @Test
    void invalidAlignmentFallsBackToDefaultInsteadOfThrowing() {
        assertDoesNotThrow(() -> HologramSettings.parseAlignment("sideways"));
        assertEquals(TextDisplayMeta.Alignment.CENTER, HologramSettings.parseAlignment("sideways"));
    }

    @Test
    void validBillboardIsCaseInsensitive() {
        assertEquals(DisplayMeta.BillboardConstrain.VERTICAL, HologramSettings.parseBillboard("vertical"));
    }

    @Test
    void invalidBillboardFallsBackToDefaultInsteadOfThrowing() {
        assertDoesNotThrow(() -> HologramSettings.parseBillboard("nonsense"));
        assertEquals(DisplayMeta.BillboardConstrain.VERTICAL, HologramSettings.parseBillboard("nonsense"));
    }
}
