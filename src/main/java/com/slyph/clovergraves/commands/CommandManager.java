package com.slyph.clovergraves.commands;

import com.slyph.clovergraves.AxGraves;
import org.bukkit.command.PluginCommand;

public final class CommandManager {
    private CommandManager() {
    }

    public static void load() {
        PluginCommand command = AxGraves.getInstance().getCommand("clovergraves");
        if (command == null) throw new IllegalStateException("clovergraves command is missing from plugin.yml");

        Commands handler = new Commands();
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    public static void reload() {
    }
}
