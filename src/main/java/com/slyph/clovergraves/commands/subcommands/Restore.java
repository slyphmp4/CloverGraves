package com.slyph.clovergraves.commands.subcommands;

import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.schedulers.CloverScheduler;
import com.slyph.clovergraves.storage.GraveRecord;
import com.slyph.clovergraves.storage.GraveStorage;
import com.slyph.clovergraves.storage.ItemSerialization;
import com.slyph.clovergraves.storage.JsonGraveStorage;
import com.slyph.clovergraves.storage.LocationCodec;
import com.slyph.clovergraves.utils.CloverLogger;
import com.slyph.clovergraves.utils.InventoryOrderSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static com.slyph.clovergraves.AxGraves.MESSAGEUTILS;

public enum Restore {
    INSTANCE;

    @SuppressWarnings("deprecation")
    public void execute(CommandSender sender, String playerName, long id) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        String displayName = player.getName() != null ? player.getName() : playerName;
        GraveStorage storage = SpawnedGraves.storage();

        if (storage == null || storage instanceof JsonGraveStorage) {
            MESSAGEUTILS.sendLang(sender, "restore.unsupported");
            return;
        }

        CloverScheduler.get().runAsync(() -> {
            Optional<GraveRecord> entry = storage.historyEntry(id);
            CloverScheduler.get().run(() -> validateAndClaim(sender, player, displayName, id, storage, entry));
        });
    }

    private void validateAndClaim(CommandSender sender, OfflinePlayer player, String displayName, long id,
                                  GraveStorage storage, Optional<GraveRecord> entryOpt) {
        if (entryOpt.isEmpty() || !entryOpt.get().owner().equals(player.getUniqueId())) {
            MESSAGEUTILS.sendLang(sender, "restore.not-found", Map.of("%id%", String.valueOf(id), "%player%", displayName));
            return;
        }

        GraveRecord entry = entryOpt.get();
        Location location = LocationCodec.deserialize(entry.location());
        if (location == null || location.getWorld() == null) {
            MESSAGEUTILS.sendLang(sender, "restore.not-found", Map.of("%id%", String.valueOf(id), "%player%", displayName));
            return;
        }

        CloverScheduler.get().runAsync(() -> {
            boolean claimed = storage.claimForRestore(id);
            CloverScheduler.get().run(() -> {
                if (!claimed) {
                    MESSAGEUTILS.sendLang(sender, "restore.already-restored", Map.of("%id%", String.valueOf(id)));
                    return;
                }
                spawn(sender, player, displayName, id, entry, location);
            });
        });
    }

    private void spawn(CommandSender sender, OfflinePlayer player, String displayName, long id,
                       GraveRecord entry, Location location) {
        try {
            ItemStack[] items = ItemSerialization.deserialize(entry.items());
            Grave grave = new Grave(location, player, Arrays.asList(items), entry.storedXP(),
                    System.currentTimeMillis(), InventoryOrderSnapshot.EMPTY);
            SpawnedGraves.addGrave(grave);

            MESSAGEUTILS.sendLang(sender, "restore.success", Map.of(
                    "%id%", String.valueOf(id),
                    "%player%", displayName,
                    "%world%", location.getWorld().getName(),
                    "%x%", String.valueOf(location.getBlockX()),
                    "%y%", String.valueOf(location.getBlockY()),
                    "%z%", String.valueOf(location.getBlockZ())
            ));
        } catch (Exception ex) {
            CloverLogger.error("failed to restore grave {}", id, ex);
            MESSAGEUTILS.sendLang(sender, "restore.failed", Map.of("%id%", String.valueOf(id)));
        }
    }
}
