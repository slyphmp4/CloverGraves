package com.artillexstudios.axgraves;

import com.artillexstudios.axapi.AxPlugin;
import com.artillexstudios.axapi.config.Config;
import com.artillexstudios.axapi.libs.boostedyaml.dvs.versioning.BasicVersioning;
import com.artillexstudios.axapi.libs.boostedyaml.settings.dumper.DumperSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.general.GeneralSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.loader.LoaderSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.updater.UpdaterSettings;
import com.artillexstudios.axapi.metrics.AxMetrics;
import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.serializers.Serializers;
import com.artillexstudios.axapi.utils.MessageUtils;
import com.artillexstudios.axapi.utils.featureflags.FeatureFlags;
import com.artillexstudios.axapi.utils.logging.LogUtils;
import com.artillexstudios.axgraves.commands.CommandManager;
import com.artillexstudios.axgraves.config.GraveSettings;
import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.GravePlaceholders;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import com.artillexstudios.axgraves.listeners.DeathListener;
import com.artillexstudios.axgraves.listeners.GraveInventoryListener;
import com.artillexstudios.axgraves.listeners.PlayerInteractListener;
import com.artillexstudios.axgraves.schedulers.SaveGraves;
import com.artillexstudios.axgraves.storage.EndReason;
import com.artillexstudios.axgraves.storage.GraveRecord;
import com.artillexstudios.axgraves.storage.GraveStorage;
import com.artillexstudios.axgraves.storage.JsonGraveStorage;
import com.artillexstudios.axgraves.utils.InventoryOrderSnapshot;
import com.artillexstudios.axgraves.utils.UpdateNotifier;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AxGraves extends AxPlugin {
    private static AxPlugin instance;
    public static Config CONFIG;
    public static Config LANG;
    public static MessageUtils MESSAGEUTILS;
    public static ScheduledExecutorService EXECUTOR;
    private static AxMetrics metrics;
    private static boolean debugMode;

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static void setDebugMode(boolean debugMode) {
        AxGraves.debugMode = debugMode;
    }

    public static AxPlugin getInstance() {
        return instance;
    }

    public void enable() {
        instance = this;

        new Metrics(this, 20332);

        CONFIG = new Config(new File(getDataFolder(), "config.yml"), getResource("config.yml"), GeneralSettings.builder().setUseDefaults(false).build(), LoaderSettings.builder().setAutoUpdate(true).build(), DumperSettings.DEFAULT, UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).build());
        LANG = new Config(new File(getDataFolder(), "messages.yml"), getResource("messages.yml"), GeneralSettings.builder().setUseDefaults(false).build(), LoaderSettings.builder().setAutoUpdate(true).build(), DumperSettings.DEFAULT, UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).build());

        debugMode = CONFIG.getBoolean("debug", false);
        MESSAGEUTILS = new MessageUtils(LANG.getBackingDocument(), "prefix", CONFIG.getBackingDocument());
        GraveSettings.reload(CONFIG);

        // created fresh on every enable (rather than as a field initializer) so a plugin
        // reload/re-enable in the same JVM never resumes with an already-shut-down executor.
        EXECUTOR = Executors.newSingleThreadScheduledExecutor();

        new DeathListener();
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(), this);
        getServer().getPluginManager().registerEvents(new GraveInventoryListener(), this);

        CommandManager.load();
        GravePlaceholders.register();

        GraveStorage storage = createStorage();
        SpawnedGraves.setStorage(storage);
        try {
            storage.init();
        } catch (Exception ex) {
            LogUtils.error("failed to initialize grave storage", ex);
        }

        if (CONFIG.getBoolean("save-graves.enabled", true)) {
            for (GraveRecord record : storage.loadAll()) {
                restoreGrave(record);
            }
        }

        SaveGraves.start();

        metrics = new AxMetrics(this, 20);
        metrics.start();

        UpdateNotifier.init(CONFIG, LANG);
        if (CONFIG.getBoolean("update-notifier.enabled", true)) new UpdateNotifier();
    }

    @org.jetbrains.annotations.NotNull
    private GraveStorage createStorage() {
        // SQL storage (H2/MySQL/SQLite via AxAPI's DatabaseHandler) is selected here once
        // storage.type is set to a SQL backend; JSON remains the always-available fallback used
        // whenever storage.type is JSON, unset, or the SQL driver could not be initialized.
        return new JsonGraveStorage(getDataFolder());
    }

    private void restoreGrave(GraveRecord record) {
        Location location = Serializers.LOCATION.deserialize(record.location());
        if (location == null || location.getWorld() == null) {
            LogUtils.warn("skipping a saved grave for {} - its world is not loaded", record.owner());
            return;
        }

        Scheduler.get().runAt(location, task -> {
            try {
                OfflinePlayer owner = Bukkit.getOfflinePlayer(record.owner());
                ItemStack[] items = Serializers.ITEM_ARRAY.deserialize(record.items());
                Grave grave = new Grave(location, owner, Arrays.asList(items), record.storedXP(), record.createdAt(), InventoryOrderSnapshot.EMPTY);
                grave.assignStorageId(record.id());
                SpawnedGraves.addGrave(grave);
            } catch (Exception ex) {
                LogUtils.error("failed to restore a saved grave for {}", record.owner(), ex);
            }
        });
    }

    public void disable() {
        if (metrics != null) metrics.cancel();

        SaveGraves.stop();

        GraveStorage storage = SpawnedGraves.storage();
        boolean persisting = CONFIG != null && CONFIG.getBoolean("save-graves.enabled", true) && storage != null;

        for (Grave grave : SpawnedGraves.getGraves()) {
            if (!persisting) {
                grave.remove(EndReason.SHUTDOWN);
                continue;
            }

            // make sure the published snapshot reflects the final state before the last flush
            grave.contents().refreshSnapshot();
            if (grave.getEntity() != null) grave.getEntity().remove();
            if (grave.getHologram() != null) grave.getHologram().remove();
        }

        if (persisting) {
            SaveGraves.flushDirty();
        }

        if (storage != null) {
            try {
                storage.close();
            } catch (Exception ex) {
                LogUtils.error("failed to close grave storage", ex);
            }
        }

        if (EXECUTOR != null) {
            EXECUTOR.shutdown();
            try {
                if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    EXECUTOR.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                EXECUTOR.shutdownNow();
            }
        }
    }

    public void updateFlags() {
        FeatureFlags.USE_LEGACY_HEX_FORMATTER.set(true);
        FeatureFlags.PACKET_ENTITY_TRACKER_ENABLED.set(true);
        FeatureFlags.HOLOGRAM_UPDATE_TICKS.set(5L);
        FeatureFlags.PACKET_ENTITY_TRACKER_THREADS.set(1);
        FeatureFlags.ENABLE_PACKET_LISTENERS.set(true);
        FeatureFlags.PLACEHOLDER_API_HOOK.set(true);
        FeatureFlags.PLACEHOLDER_API_IDENTIFIER.set("axgraves");
    }
}
