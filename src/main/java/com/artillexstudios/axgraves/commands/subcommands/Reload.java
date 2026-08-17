package com.artillexstudios.axgraves.commands.subcommands;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axgraves.AxGraves;
import com.artillexstudios.axgraves.config.GraveSettings;
import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.GravePlaceholders;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import com.artillexstudios.axgraves.listeners.DeathListener;
import com.artillexstudios.axgraves.schedulers.SaveGraves;
import com.artillexstudios.axgraves.utils.UpdateNotifier;
import org.bukkit.command.CommandSender;

import java.util.Map;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;
import static com.artillexstudios.axgraves.AxGraves.LANG;
import static com.artillexstudios.axgraves.AxGraves.MESSAGEUTILS;

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
        GravePlaceholders.reload();
        UpdateNotifier.reload();
        SaveGraves.start();

        // hologram text/appearance can change on reload - rebuild each one on the region that
        // actually owns it. The old code dispatched this to the save EXECUTOR, which read/wrote
        // packet-entity and hologram state off its owning thread.
        for (Grave grave : SpawnedGraves.getGraves()) {
            Scheduler.get().runAt(grave.getLocation(), task -> grave.updateHologram());
        }

        MESSAGEUTILS.sendLang(sender, "reload.success");
    }
}
