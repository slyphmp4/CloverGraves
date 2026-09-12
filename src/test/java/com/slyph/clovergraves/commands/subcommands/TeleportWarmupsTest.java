package com.slyph.clovergraves.commands.subcommands;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TeleportWarmupsTest {
    @Test
    void cancelledCallbacksCannotCompleteOrClearANewAttemptAtTheSameLocation() {
        UUID player = UUID.randomUUID();
        Location origin = new Location(null, 0, 64, 0);
        TeleportWarmups.startPending(player, origin);
        TeleportWarmups.Pending old = TeleportWarmups.get(player);
        TeleportWarmups.clear(player);
        TeleportWarmups.startPending(player, origin);
        TeleportWarmups.Pending current = TeleportWarmups.get(player);
        assertFalse(TeleportWarmups.isCurrent(player, old));
        assertFalse(TeleportWarmups.clear(player, old));
        assertTrue(TeleportWarmups.isCurrent(player, current));
        assertTrue(TeleportWarmups.clear(player, current));
        assertFalse(TeleportWarmups.isPending(player));
    }

    @Test
    void originIsCapturedIndependentlyOfCallerLocation() {
        UUID player = UUID.randomUUID();
        Location origin = new Location(null, 0, 64, 0);
        TeleportWarmups.startPending(player, origin);
        origin.setX(100);
        assertEquals(0, TeleportWarmups.get(player).origin().getX());
        TeleportWarmups.clear(player);
    }
}
