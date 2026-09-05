package com.slyph.clovergraves;

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
import com.artillexstudios.axapi.utils.featureflags.FeatureFlags;
import com.artillexstudios.axapi.utils.logging.LogUtils;
import com.slyph.clovergraves.commands.CommandManager;
import com.slyph.clovergraves.config.GraveSettings;
import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.GravePlaceholders;
import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.listeners.DeathListener;
import com.slyph.clovergraves.listeners.GraveEntityInteractListener;
import com.slyph.clovergraves.listeners.GraveInventoryListener;
import com.slyph.clovergraves.listeners.PlayerInteractListener;
import com.slyph.clovergraves.listeners.TeleportCancelListener;
import com.slyph.clovergraves.schedulers.SaveGraves;
import com.slyph.clovergraves.storage.EndReason;
import com.slyph.clovergraves.storage.GraveRecord;
import com.slyph.clovergraves.storage.GraveStorage;
import com.slyph.clovergraves.storage.ItemSerialization;
import com.slyph.clovergraves.storage.JsonGraveStorage;
import com.slyph.clovergraves.storage.SqlDrivers;
import com.slyph.clovergraves.storage.SqlGraveStorage;
import com.slyph.clovergraves.storage.StorageMigration;
import com.slyph.clovergraves.utils.InventoryOrderSnapshot;
import com.slyph.clovergraves.utils.MessageService;
import com.slyph.clovergraves.utils.UpdateNotifier;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AxGraves extends AxPlugin {
    private static AxPlugin instance;
    public static Config CONFIG;
    public static Config LANG;
    public static MessageService MESSAGEUTILS;
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
        MESSAGEUTILS = new MessageService(LANG, "prefix", CONFIG);
        GraveSettings.reload(CONFIG);

        EXECUTOR = Executors.newSingleThreadScheduledExecutor();

        new DeathListener();
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(), this);
        getServer().getPluginManager().registerEvents(new GraveEntityInteractListener(), this);
        getServer().getPluginManager().registerEvents(new GraveInventoryListener(), this);
        getServer().getPluginManager().registerEvents(new TeleportCancelListener(), this);

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

        String brand = (Bukkit.getName() + " " + Bukkit.getVersion()).toLowerCase(Locale.ROOT);
        if (brand.contains("cardboard") && "26.2".equals(Bukkit.getMinecraftVersion())) {
            getLogger().info("Cardboard 26.2 detected. Cardboard-safe Bukkit entity and interaction backend enabled.");
        } else if (!"26.2".equals(Bukkit.getMinecraftVersion())) {
            getLogger().warning("CloverGraves is built and tested against Minecraft/Cardboard 26.2. Current Minecraft version: " + Bukkit.getMinecraftVersion());
        }
    }

    @Override
    public void dependencies(DependencyManagerWrapper wrapper) {
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
        } catch (Throwable throwable) {
            LogUtils.error("failed to initialize {} storage - falling back to the JSON file backend (no grave history/restore until this is fixed)", type, throwable);
            JsonGraveStorage json = new JsonGraveStorage(getDataFolder());
            json.init();
            return json;
        }
    }

    @NotNull
    private DatabaseConfig embeddedConfig(com.artillexstudios.axapi.database.DatabaseType type, String tablePrefix) {
        DatabaseConfig config = new DatabaseConfig();
        config.type = type;
        config.database = new File(getDataFolder(), "data").getAbsolutePath();
        config.tablePrefix(tablePrefix);
        return config;
    }

    @NotNull
    private DatabaseConfig mysqlConfig(String tablePrefix) {
        DatabaseConfig config = new DatabaseConfig();
        config.type = new MySQLDatabaseType();
        config.address = CONFIG.getString("storage.mysql.address", "127.0.0.1");
        config.port = CONFIG.getInt("storage.mysql.port", 3306);
        config.database = CONFIG.getString("storage.mysql.database", "axgraves");
        config.username = CONFIG.getString("storage.mysql.username", "root");
        config.password = CONFIG.getString("storage.mysql.password", "");
        config.tablePrefix(tablePrefix);

        if (config.pool == null) config.pool = new DatabaseConfig.Pool();
        config.pool.maximumPoolSize = CONFIG.getInt("storage.mysql.pool.maximum-pool-size", 10);
        config.pool.minimumIdle = CONFIG.getInt("storage.mysql.pool.minimum-idle", 10);

        return config;
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
                ItemStack[] items = ItemSerialization.deserialize(record.items());
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

            try {
                grave.contents().refreshSnapshot();
            } catch (Exception ex) {
                LogUtils.error("failed to refresh snapshot for a grave during shutdown - it may not reflect its final state", ex);
            }
            if (grave.getEntity() != null) grave.getEntity().remove();
            if (grave.getHologram() != null) grave.getHologram().remove();
        }

        if (persisting) SaveGraves.flushDirty();

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
                if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) EXECUTOR.shutdownNow();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                EXECUTOR.shutdownNow();
            }
        }
    }

    public void updateFlags() {
        FeatureFlags.USE_LEGACY_HEX_FORMATTER.set(true);
        FeatureFlags.PACKET_ENTITY_TRACKER_ENABLED.set(false);
        FeatureFlags.PACKET_ENTITY_TRACKER_THREADS.set(1);
        FeatureFlags.ENABLE_PACKET_LISTENERS.set(false);
        FeatureFlags.PLACEHOLDER_API_HOOK.set(true);
        FeatureFlags.PLACEHOLDER_API_IDENTIFIER.set("axgraves");
    }
}
