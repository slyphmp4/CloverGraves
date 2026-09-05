package com.slyph.clovergraves.commands.subcommands;

import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.schedulers.CloverScheduler;
import com.slyph.clovergraves.storage.GraveRecord;
import com.slyph.clovergraves.storage.GraveStorage;
import com.slyph.clovergraves.storage.ItemSerialization;
import com.slyph.clovergraves.storage.JsonGraveStorage;
import com.slyph.clovergraves.storage.LocationCodec;
import com.slyph.clovergraves.utils.TextFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

import static com.slyph.clovergraves.AxGraves.MESSAGEUTILS;

public enum History {
    INSTANCE;

    private static final int DISPLAY_LIMIT = 20;

    @SuppressWarnings("deprecation")
    public void execute(CommandSender sender, String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        String displayName = player.getName() != null ? player.getName() : playerName;
        GraveStorage storage = SpawnedGraves.storage();

        if (storage == null || storage instanceof JsonGraveStorage) {
            MESSAGEUTILS.sendLang(sender, "restore.unsupported");
            return;
        }

        CloverScheduler.get().runAsync(() -> {
            List<GraveRecord> entries = storage.history(player.getUniqueId(), DISPLAY_LIMIT);
            CloverScheduler.get().run(() -> render(sender, displayName, entries));
        });
    }

    private void render(CommandSender sender, String displayName, List<GraveRecord> entries) {
        if (entries.isEmpty()) {
            MESSAGEUTILS.sendLang(sender, "history.empty", Map.of("%player%", displayName));
            return;
        }

        MESSAGEUTILS.sendLang(sender, "history.header", Map.of("%player%", displayName));
        for (GraveRecord entry : entries) {
            MESSAGEUTILS.sendLang(sender, entry.restored() ? "history.entry-restored" : "history.entry", describeEntry(entry));
        }
    }

    private Map<String, String> describeEntry(GraveRecord entry) {
        Location location = LocationCodec.deserialize(entry.location());
        String world = location != null && location.getWorld() != null ? location.getWorld().getName() : "?";
        int x = location != null ? location.getBlockX() : 0;
        int y = location != null ? location.getBlockY() : 0;
        int z = location != null ? location.getBlockZ() : 0;

        int items = 0;
        try {
            for (ItemStack item : ItemSerialization.deserialize(entry.items())) {
                if (item != null && !item.getType().isAir()) items++;
            }
        } catch (Exception ignored) {
        }

        long endedAt = entry.endedAt() != null ? entry.endedAt() : entry.createdAt();
        String reason = entry.endReason() != null ? entry.endReason().name().toLowerCase() : "unknown";

        return Map.of(
                "%id%", String.valueOf(entry.id()),
                "%world%", world,
                "%x%", String.valueOf(x),
                "%y%", String.valueOf(y),
                "%z%", String.valueOf(z),
                "%items%", String.valueOf(items),
                "%xp%", String.valueOf(entry.storedXP()),
                "%reason%", reason,
                "%time%", TextFormatter.formatTime(System.currentTimeMillis() - endedAt)
        );
    }
}
