package com.slyph.clovergraves.commands.subcommands;

import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.utils.LocationUtils;
import com.slyph.clovergraves.utils.TextFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

import static com.slyph.clovergraves.AxGraves.CONFIG;
import static com.slyph.clovergraves.AxGraves.LANG;
import static com.slyph.clovergraves.AxGraves.MESSAGEUTILS;

public enum List {
    INSTANCE;

    public void execute(CommandSender sender) {
        boolean found = false;
        int despawnTime = CONFIG.getInt("despawn-time-seconds", 1800);

        for (Grave grave : SpawnedGraves.getGraves()) {
            if (sender instanceof Player player && !grave.getPlayer().getUniqueId().equals(player.getUniqueId())
                    && !sender.hasPermission("axgraves.list.other")) {
                continue;
            }

            Location location = grave.getLocation();
            long remaining = despawnTime != -1
                    ? Math.max(0L, despawnTime * 1_000L - (System.currentTimeMillis() - grave.getSpawned()))
                    : System.currentTimeMillis() - grave.getSpawned();
            Map<String, String> replacements = Map.of(
                    "%player%", grave.getPlayerName(),
                    "%world%", LocationUtils.getWorldName(location.getWorld()),
                    "%x%", String.valueOf(location.getBlockX()),
                    "%y%", String.valueOf(location.getBlockY()),
                    "%z%", String.valueOf(location.getBlockZ()),
                    "%time%", TextFormatter.formatTime(remaining)
            );

            if (!found) {
                MESSAGEUTILS.sendFormatted(sender, LANG.getFirstLine("grave-list.header", ""));
                found = true;
            }

            String command = String.format(Locale.ROOT, "/clovergraves tp %s %.4f %.4f %.4f",
                    location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
            Component line = TextFormatter.format(TextFormatter.replace(LANG.getFirstLine("grave-list.grave", ""), replacements))
                    .clickEvent(ClickEvent.runCommand(command));
            sender.sendMessage(line);
        }

        if (!found) MESSAGEUTILS.sendLang(sender, "grave-list.no-graves");
    }
}
