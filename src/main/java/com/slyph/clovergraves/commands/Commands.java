package com.slyph.clovergraves.commands;

import com.slyph.clovergraves.commands.subcommands.Help;
import com.slyph.clovergraves.commands.subcommands.History;
import com.slyph.clovergraves.commands.subcommands.List;
import com.slyph.clovergraves.commands.subcommands.Reload;
import com.slyph.clovergraves.commands.subcommands.Restore;
import com.slyph.clovergraves.commands.subcommands.Teleport;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Locale;

import static com.slyph.clovergraves.AxGraves.MESSAGEUTILS;

public final class Commands implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (!require(sender, "axgraves.help")) return true;
            Help.INSTANCE.execute(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!require(sender, "axgraves.reload")) return true;
                Reload.INSTANCE.execute(sender);
            }
            case "list" -> {
                if (!require(sender, "axgraves.list")) return true;
                List.INSTANCE.execute(sender);
            }
            case "tp" -> executeTeleport(sender, args, 1);
            case "grave" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("tp")) executeTeleport(sender, args, 2);
                else MESSAGEUTILS.sendLang(sender, "commands.invalid-command");
            }
            case "history" -> {
                if (!require(sender, "axgraves.history")) return true;
                if (args.length < 2) {
                    MESSAGEUTILS.sendLang(sender, "commands.missing-argument", java.util.Map.of("%value%", "player"));
                    return true;
                }
                History.INSTANCE.execute(sender, args[1]);
            }
            case "restore" -> {
                if (!require(sender, "axgraves.restore")) return true;
                if (args.length < 3) {
                    MESSAGEUTILS.sendLang(sender, "commands.missing-argument", java.util.Map.of("%value%", args.length < 2 ? "player" : "id"));
                    return true;
                }
                try {
                    Restore.INSTANCE.execute(sender, args[1], Long.parseLong(args[2]));
                } catch (NumberFormatException ex) {
                    MESSAGEUTILS.sendLang(sender, "commands.invalid-value", java.util.Map.of("%value%", args[2]));
                }
            }
            default -> MESSAGEUTILS.sendLang(sender, "commands.invalid-command");
        }
        return true;
    }

    private void executeTeleport(CommandSender sender, String[] args, int offset) {
        if (!require(sender, "axgraves.tp")) return;
        if (!(sender instanceof Player player)) {
            MESSAGEUTILS.sendLang(sender, "commands.player-only");
            return;
        }

        if (args.length == offset) {
            Teleport.INSTANCE.execute(player, null, null, null, null);
            return;
        }

        if (args.length < offset + 4) {
            MESSAGEUTILS.sendLang(sender, "commands.missing-argument", java.util.Map.of("%value%", "world x y z"));
            return;
        }

        World world = Bukkit.getWorld(args[offset]);
        if (world == null) {
            MESSAGEUTILS.sendLang(sender, "commands.invalid-value", java.util.Map.of("%value%", args[offset]));
            return;
        }

        try {
            double x = Double.parseDouble(args[offset + 1]);
            double y = Double.parseDouble(args[offset + 2]);
            double z = Double.parseDouble(args[offset + 3]);
            Teleport.INSTANCE.execute(player, world, x, y, z);
        } catch (NumberFormatException ex) {
            MESSAGEUTILS.sendLang(sender, "commands.invalid-value", java.util.Map.of("%value%", "coordinates"));
        }
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        MESSAGEUTILS.sendLang(sender, "commands.no-permission");
        return false;
    }

    @Override
    @Nullable
    public java.util.List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            java.util.List<String> choices = new ArrayList<>();
            addIfAllowed(choices, sender, "help", "axgraves.help");
            addIfAllowed(choices, sender, "reload", "axgraves.reload");
            addIfAllowed(choices, sender, "list", "axgraves.list");
            addIfAllowed(choices, sender, "tp", "axgraves.tp");
            addIfAllowed(choices, sender, "grave", "axgraves.tp");
            addIfAllowed(choices, sender, "history", "axgraves.history");
            addIfAllowed(choices, sender, "restore", "axgraves.restore");
            return filter(choices, args[0]);
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (first.equals("grave") && sender.hasPermission("axgraves.tp")) return filter(java.util.List.of("tp"), args[1]);
            if ((first.equals("history") && sender.hasPermission("axgraves.history"))
                    || (first.equals("restore") && sender.hasPermission("axgraves.restore"))) {
                java.util.List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList();
                return filter(players, args[1]);
            }
            if (first.equals("tp") && sender.hasPermission("axgraves.tp.bypass")) {
                return filter(Bukkit.getWorlds().stream().map(World::getName).toList(), args[1]);
            }
        }

        if (first.equals("tp") && sender.hasPermission("axgraves.tp.bypass") && sender instanceof Player player) {
            int coordinateIndex = args.length - 2;
            if (coordinateIndex >= 1 && coordinateIndex <= 3) {
                double value = switch (coordinateIndex) {
                    case 1 -> player.getLocation().getX();
                    case 2 -> player.getLocation().getY();
                    default -> player.getLocation().getZ();
                };
                return java.util.List.of(String.format(Locale.ROOT, "%.2f", value));
            }
        }

        return java.util.List.of();
    }

    private void addIfAllowed(java.util.List<String> values, CommandSender sender, String value, String permission) {
        if (sender.hasPermission(permission)) values.add(value);
    }

    private java.util.List<String> filter(java.util.List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
