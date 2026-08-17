package com.artillexstudios.axgraves;

import com.artillexstudios.axapi.AxPlugin;
import com.artillexstudios.axapi.config.Config;
import com.artillexstudios.axapi.database.DatabaseConfig;
import com.artillexstudios.axapi.database.impl.H2DatabaseType;
import com.artillexstudios.axapi.database.impl.MySQLDatabaseType;
import com.artillexstudios.axapi.database.impl.SQLiteDatabaseType;
import com.artillexstudios.axapi.dependencies.DependencyManagerWrapper;
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
import com.artillexstudios.axgraves.storage.SqlDrivers;
import com.artillexstudios.axgraves.storage.SqlGraveStorage;
import com.artillexstudios.axgraves.storage.StorageMigration;
import com.artillexstudios.axgraves.utils.InventoryOrderSnapshot;
import com.artillexstudios.axgraves.utils.UpdateNotifier;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

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

    @Override
    public void dependencies(DependencyManagerWrapper wrapper) {
        // runs during onLoad(), before CONFIG exists - fetched unconditionally since there's no
        // config to gate on yet. H2 is the default storage.type; see SqlDrivers for why MySQL
        // isn't fetched here.
        SqlDrivers.declare(wrapper);
    }

    @NotNull
    private GraveStorage createStorage() {
        String type = CONFIG.getString("storage.type", "H2").trim().toUpperCase();
        boolean historyEnabled = CONFIG.getBoolean("history.enabled", true);
        int keepPerPlayer = CONFIG.getInt("history.keep-per-player", 5);
        int keepDays = CONFIG.getInt("history.keep-days", 14);
        String tablePrefix = CONFIG.getString("storage.table-prefix", "axgraves_");

        try {
            DatabaseConfig dbConfig = switch (type) {
                case "SQLITE" -> embeddedConfig(new SQLiteDatabaseType(SqlDrivers.SQLITE_RELOCATION), tablePrefix);
                case "MYSQL" -> mysqlConfig(tablePrefix);
                default -> embeddedConfig(new H2DatabaseType(SqlDrivers.H2_RELOCATION), tablePrefix);
            };

            SqlGraveStorage sql = new SqlGraveStorage(dbConfig, historyEnabled, keepPerPlayer, keepDays);
            sql.init();
            StorageMigration.migrateIfNeeded(getDataFolder(), sql);
            return sql;
        } catch (Throwable t) {
            LogUtils.error("failed to initialize {} storage - falling back to the JSON file backend (no grave history/restore until this is fixed)", type, t);
            JsonGraveStorage json = new JsonGraveStorage(getDataFolder());
            json.init();
            return json;
        }
    }

    @NotNull
    private DatabaseConfig embeddedConfig(com.artillexstudios.axapi.database.DatabaseType type, String tablePrefix) {
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.type = type;
        cfg.database = new File(getDataFolder(), "data").getAbsolutePath();
        cfg.tablePrefix(tablePrefix);
        return cfg;
    }

    @NotNull
    private DatabaseConfig mysqlConfig(String tablePrefix) {
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.type = new MySQLDatabaseType();
        cfg.address = CONFIG.getString("storage.mysql.address", "127.0.0.1");
        cfg.port = CONFIG.getInt("storage.mysql.port", 3306);
        cfg.database = CONFIG.getString("storage.mysql.database", "axgraves");
        cfg.username = CONFIG.getString("storage.mysql.username", "root");
        cfg.password = CONFIG.getString("storage.mysql.password", "");
        cfg.tablePrefix(tablePrefix);

        if (cfg.pool == null) cfg.pool = new DatabaseConfig.Pool();
        cfg.pool.maximumPoolSize = CONFIG.getInt("storage.mysql.pool.maximum-pool-size", 10);
        cfg.pool.minimumIdle = CONFIG.getInt("storage.mysql.pool.minimum-idle", 10);

        return cfg;
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
