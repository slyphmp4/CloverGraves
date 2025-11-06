package com.artillexstudios.axgraves.commands;

import com.artillexstudios.axgraves.commands.subcommands.Teleport;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.DefaultFor;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.AutoComplete;
import revxrsal.commands.bukkit.annotation.CommandPermission;

/**
 * Separate command for /gtp alias - Made by dei0 (dei2004)
 * This allows players to use /gtp as a shortcut to teleport to their grave
 */
@Command("gtp")
public class GTPCommand {
    
    @DefaultFor("~")
    @CommandPermission("axgraves.tp")
    @AutoComplete("@nothing @nothing @nothing @nothing")
    public void gtp(@NotNull Player sender, @Optional World world, @Optional Double x, @Optional Double y, @Optional Double z) {
        Teleport.INSTANCE.execute(sender, world, x, y, z);
    }
}
