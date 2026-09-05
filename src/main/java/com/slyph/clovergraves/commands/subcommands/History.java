package com.slyph.clovergraves.commands.subcommands;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.serializers.Serializers;
import com.artillexstudios.axapi.utils.StringUtils;
import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.storage.GraveRecord;
import com.slyph.clovergraves.storage.GraveStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

import static com.slyph.clovergraves.AxGraves.MESSAGEUTILS;

/** {@code /axgraves history <player>} - lists a player's past graves. Feature C (admin recovery). */
public enum History {
    INSTANCE;

    private static final int DISPLAY_LIMIT = 20;

    @SuppressWarnings("deprecation") // Bukkit#getOfflinePlayer(String) may block on a web lookup - deliberately called only inside the async task below
    public void execute(CommandSender sender, String playerName) {
        // OfflinePlayer-by-name can block on network IO for an uncached name, and GraveStorage
        // must never be touched from the main/region thread - so the whole lookup runs off it.
        Scheduler.get().runAsync(() -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
            String displayName = player.getName() != null ? player.getName() : playerName;

            GraveStorage storage = SpawnedGraves.storage();
            if (storage == null) {
                MESSAGEUTILS.sendLang(sender, "restore.unsupported");
                return;
            }

            List<GraveRecord> entries = storage.history(player.getUniqueId(), DISPLAY_LIMIT);
            if (entries.isEmpty()) {
                MESSAGEUTILS.sendLang(sender, "history.empty", Map.of("%player%", displayName));
                return;
            }

            MESSAGEUTILS.sendLang(sender, "history.header", Map.of("%player%", displayName));
            for (GraveRecord entry : entries) {
                String template = entry.restored() ? "history.entry-restored" : "history.entry";
                MESSAGEUTILS.sendLang(sender, template, describeEntry(entry));
            }
        });
    }

    private Map<String, String> describeEntry(GraveRecord entry) {
        Location location = Serializers.LOCATION.deserialize(entry.location());
        String world = location != null && location.getWorld() != null ? location.getWorld().getName() : "?";
        int x = location != null ? location.getBlockX() : 0;
        int y = location != null ? location.getBlockY() : 0;
        int z = location != null ? location.getBlockZ() : 0;

        int items = 0;
        try {
            for (ItemStack it : Serializers.ITEM_ARRAY.deserialize(entry.items())) {
                if (it != null && !it.getType().isAir()) items++;
            }
        } catch (Exception ignored) {
            // a row from before an MC version bump might not deserialize - the count just
            // shows 0 rather than failing the whole listing
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
                "%time%", StringUtils.formatTime(System.currentTimeMillis() - endedAt)
        );
    }
}
