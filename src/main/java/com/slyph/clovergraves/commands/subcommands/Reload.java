package com.slyph.clovergraves.commands.subcommands;

import com.slyph.clovergraves.AxGraves;
import com.slyph.clovergraves.config.GraveSettings;
import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.listeners.DeathListener;
import com.slyph.clovergraves.schedulers.CloverScheduler;
import com.slyph.clovergraves.schedulers.SaveGraves;
import com.slyph.clovergraves.utils.UpdateNotifier;
import org.bukkit.command.CommandSender;

import java.util.Map;

import static com.slyph.clovergraves.AxGraves.CONFIG;
import static com.slyph.clovergraves.AxGraves.LANG;
import static com.slyph.clovergraves.AxGraves.MESSAGEUTILS;

public enum Reload {
    INSTANCE;

    public void execute(CommandSender sender) {
        if (!CONFIG.reload()) {
            MESSAGEUTILS.sendLang(sender, "reload.failed", Map.of("%file%", "config.yml"));
            return;
        }

        if (!LANG.reload()) {
            MESSAGEUTILS.sendLang(sender, "reload.failed", Map.of("%file%", "messages.yml"));
            return;
        }

        AxGraves.setDebugMode(CONFIG.getBoolean("debug", false));
        GraveSettings.reload(CONFIG);
        DeathListener.reload();
        UpdateNotifier.reload();
        SaveGraves.start();

        for (Grave grave : SpawnedGraves.getGraves()) {
            CloverScheduler.get().runAt(grave.getLocation(), grave::updateHologram);
        }

        MESSAGEUTILS.sendLang(sender, "reload.success");
    }
}
