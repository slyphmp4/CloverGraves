package com.slyph.clovergraves.commands.subcommands;

import com.slyph.clovergraves.utils.TextFormatter;
import org.bukkit.command.CommandSender;

import static com.slyph.clovergraves.AxGraves.LANG;

public enum Help {
    INSTANCE;

    public void execute(CommandSender sender) {
        for (String line : LANG.getStringList("help")) {
            if (line.isBlank() || line.equalsIgnoreCase("&7")) sender.sendMessage("");
            else sender.sendMessage(TextFormatter.format(line));
        }
    }
}
