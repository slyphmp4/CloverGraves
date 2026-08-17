package com.artillexstudios.axgraves.commands.subcommands;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.serializers.Serializers;
import com.artillexstudios.axapi.utils.logging.LogUtils;
import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import com.artillexstudios.axgraves.storage.GraveRecord;
import com.artillexstudios.axgraves.storage.GraveStorage;
import com.artillexstudios.axgraves.utils.InventoryOrderSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static com.artillexstudios.axgraves.AxGraves.MESSAGEUTILS;

/**
 * {@code /axgraves restore <player> <id>} - re-spawns a past grave from history. Feature C
 * (admin recovery). {@link GraveStorage#claimForRestore(long)} is the anti-duplication guard:
 * it's a single atomic UPDATE ... WHERE restored = FALSE, so two admins racing the same id can
 * never both succeed.
 */
public enum Restore {
    INSTANCE;

    @SuppressWarnings("deprecation") // Bukkit#getOfflinePlayer(String) may block on a web lookup - deliberately called only inside the async task below
    public void execute(CommandSender sender, String playerName, long id) {
        Scheduler.get().runAsync(() -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
            String displayName = player.getName() != null ? player.getName() : playerName;

            GraveStorage storage = SpawnedGraves.storage();
            if (storage == null) {
                MESSAGEUTILS.sendLang(sender, "restore.unsupported");
                return;
            }

            Optional<GraveRecord> entryOpt = storage.historyEntry(id);
            if (entryOpt.isEmpty() || !entryOpt.get().owner().equals(player.getUniqueId())) {
                MESSAGEUTILS.sendLang(sender, "restore.not-found", Map.of("%id%", String.valueOf(id), "%player%", displayName));
                return;
            }

            GraveRecord entry = entryOpt.get();
            Location location = Serializers.LOCATION.deserialize(entry.location());
            if (location == null || location.getWorld() == null) {
                MESSAGEUTILS.sendLang(sender, "restore.not-found", Map.of("%id%", String.valueOf(id), "%player%", displayName));
                return;
            }

            if (!storage.claimForRestore(id)) {
                MESSAGEUTILS.sendLang(sender, "restore.already-restored", Map.of("%id%", String.valueOf(id)));
                return;
            }

            Scheduler.get().runAt(location, spawnTask -> {
                try {
                    ItemStack[] items = Serializers.ITEM_ARRAY.deserialize(entry.items());
                    Grave grave = new Grave(location, player, Arrays.asList(items), entry.storedXP(),
                            System.currentTimeMillis(), InventoryOrderSnapshot.EMPTY);
                    SpawnedGraves.addGrave(grave);

                    Map<String, String> map = Map.of(
                            "%id%", String.valueOf(id),
                            "%player%", displayName,
                            "%world%", location.getWorld().getName(),
                            "%x%", String.valueOf(location.getBlockX()),
                            "%y%", String.valueOf(location.getBlockY()),
                            "%z%", String.valueOf(location.getBlockZ())
                    );
                    Scheduler.get().runAsync(() -> MESSAGEUTILS.sendLang(sender, "restore.success", map));
                } catch (Exception ex) {
                    LogUtils.error("failed to restore grave {}", id, ex);
                    Scheduler.get().runAsync(() -> MESSAGEUTILS.sendLang(sender, "restore.failed", Map.of("%id%", String.valueOf(id))));
                }
            });
        });
    }
}
