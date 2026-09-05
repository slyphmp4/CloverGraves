package com.slyph.clovergraves.commands;

import com.slyph.clovergraves.commands.subcommands.Help;
import com.slyph.clovergraves.commands.subcommands.History;
import com.slyph.clovergraves.commands.subcommands.List;
import com.slyph.clovergraves.commands.subcommands.Reload;
import com.slyph.clovergraves.commands.subcommands.Restore;
import com.slyph.clovergraves.commands.subcommands.Teleport;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.annotation.DefaultFor;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.orphan.OrphanCommand;

public class Commands implements OrphanCommand {

    @DefaultFor({"~", "~ help"})
    @CommandPermission("axgraves.help")
    public void help(@NotNull CommandSender sender) {
        Help.INSTANCE.execute(sender);
    }

    @Subcommand("reload")
    @CommandPermission("axgraves.reload")
    public void reload(@NotNull CommandSender sender) {
        Reload.INSTANCE.execute(sender);
    }

    @Subcommand("list")
    @CommandPermission("axgraves.list")
    public void list(@NotNull CommandSender sender) {
        List.INSTANCE.execute(sender);
    }

    @Subcommand("tp")
    @CommandPermission("axgraves.tp")
    public void tp(@NotNull Player sender, @Optional World world, @Optional Double x, @Optional Double y, @Optional Double z) {
        Teleport.INSTANCE.execute(sender, world, x, y, z);
    }

    // same command, reachable as a nested path too (e.g. /bibingka grave tp) - see Teleport
    @Subcommand("grave tp")
    @CommandPermission("axgraves.tp")
    public void graveTp(@NotNull Player sender, @Optional World world, @Optional Double x, @Optional Double y, @Optional Double z) {
        Teleport.INSTANCE.execute(sender, world, x, y, z);
    }

    @Subcommand("history")
    @CommandPermission("axgraves.history")
    public void history(@NotNull CommandSender sender, @NotNull String player) {
        History.INSTANCE.execute(sender, player);
    }

    @Subcommand("restore")
    @CommandPermission("axgraves.restore")
    public void restore(@NotNull CommandSender sender, @NotNull String player, long id) {
        Restore.INSTANCE.execute(sender, player, id);
    }
}
