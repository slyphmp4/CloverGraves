package com.slyph.clovergraves;

import com.slyph.clovergraves.commands.CommandManager;
import com.slyph.clovergraves.compat.CardboardCompatibilitySelfTest;
import com.slyph.clovergraves.config.CloverConfig;
import com.slyph.clovergraves.config.GraveSettings;
import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.hooks.placeholder.PlaceholderHook;
import com.slyph.clovergraves.listeners.DeathListener;
import com.slyph.clovergraves.listeners.GraveEntityInteractListener;
import com.slyph.clovergraves.listeners.GraveInventoryListener;
import com.slyph.clovergraves.listeners.PlayerInteractListener;
import com.slyph.clovergraves.listeners.TeleportCancelListener;
import com.slyph.clovergraves.schedulers.CloverScheduler;
import com.slyph.clovergraves.schedulers.SaveGraves;
import com.slyph.clovergraves.storage.EndReason;
import com.slyph.clovergraves.storage.GraveRecord;
import com.slyph.clovergraves.storage.GraveStorage;
import com.slyph.clovergraves.storage.ItemSerialization;
import com.slyph.clovergraves.storage.JdbcConfig;
import com.slyph.clovergraves.storage.JsonGraveStorage;
import com.slyph.clovergraves.storage.LocationCodec;
import com.slyph.clovergraves.storage.SqlGraveStorage;
import com.slyph.clovergraves.storage.StorageMigration;
import com.slyph.clovergraves.utils.CloverLogger;
import com.slyph.clovergraves.utils.InventoryOrderSnapshot;
import com.slyph.clovergraves.utils.MessageService;
import com.slyph.clovergraves.utils.UpdateNotifier;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AxGraves extends JavaPlugin {
    private static AxGraves instance;
    public static CloverConfig CONFIG;
    public static CloverConfig LANG;
    public static MessageService MESSAGEUTILS;
    public static ScheduledExecutorService EXECUTOR;
    private static boolean debugMode;

    @NotNull
    public static AxGraves getInstance() {
        if (instance == null) throw new IllegalStateException("CloverGraves is not enabled");
        return instance;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static void setDebugMode(boolean debugMode) {
        AxGraves.debugMode = debugMode;
    }

    @Override
    public void onEnable() {
        instance = this;
        CloverLogger.bind(getLogger());
        CloverScheduler.init(this);

        CONFIG = new CloverConfig(this, "config.yml");
        LANG = new CloverConfig(this, "messages.yml");
        MESSAGEUTILS = new MessageService(LANG, "prefix", CONFIG);
        debugMode = CONFIG.getBoolean("debug", false);
        GraveSettings.reload(CONFIG);

        EXECUTOR = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
                .name("CloverGraves-Storage", 0)
                .daemon(true)
                .factory());

        try {
            new Metrics(this, 20332);
        } catch (Throwable ex) {
            CloverLogger.warn("bStats could not be initialized: {}", ex.getMessage());
        }

        new DeathListener();
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(), this);
        getServer().getPluginManager().registerEvents(new GraveEntityInteractListener(), this);
        getServer().getPluginManager().registerEvents(new GraveInventoryListener(), this);
        getServer().getPluginManager().registerEvents(new TeleportCancelListener(), this);

        CommandManager.load();
        PlaceholderHook.register();

        GraveStorage storage = createStorage();
        SpawnedGraves.setStorage(storage);
        if (CONFIG.getBoolean("save-graves.enabled", true)) {
            for (GraveRecord record : storage.loadAll()) restoreGrave(record);
        }

        SaveGraves.start();
        UpdateNotifier.init(CONFIG);
        if (CONFIG.getBoolean("update-notifier.enabled", true)) new UpdateNotifier();

        String server = (Bukkit.getName() + " " + Bukkit.getVersion()).toLowerCase(Locale.ROOT);
        if (server.contains("cardboard") && "26.2".equals(Bukkit.getMinecraftVersion())) {
            CloverLogger.info("Cardboard 26.2 detected; CloverGraves is using native Bukkit TextDisplay holograms");
        } else if (!"26.2".equals(Bukkit.getMinecraftVersion())) {
            CloverLogger.warn("CloverGraves targets Minecraft/Cardboard 26.2; detected Minecraft {}", Bukkit.getMinecraftVersion());
        }

        if (Boolean.getBoolean("clovergraves.compatTest")) {
            Bukkit.getScheduler().runTaskLater(this, CardboardCompatibilitySelfTest::run, 20L);
        }
    }

    @NotNull
    private GraveStorage createStorage() {
        String type = CONFIG.getString("storage.type", "H2").trim().toUpperCase(Locale.ROOT);
        boolean historyEnabled = CONFIG.getBoolean("history.enabled", true);
        int keepPerPlayer = CONFIG.getInt("history.keep-per-player", 5);
        int keepDays = CONFIG.getInt("history.keep-days", 14);
        String tablePrefix = CONFIG.getString("storage.table-prefix", "axgraves_");

        try {
            JdbcConfig jdbc = switch (type) {
                case "SQLITE" -> sqliteConfig(tablePrefix);
                case "MYSQL" -> mysqlConfig(tablePrefix);
                default -> h2Config(tablePrefix);
            };
            SqlGraveStorage sql = new SqlGraveStorage(jdbc, historyEnabled, keepPerPlayer, keepDays);
            sql.init();
            StorageMigration.migrateIfNeeded(getDataFolder(), sql);
            return sql;
        } catch (Throwable throwable) {
            CloverLogger.error("failed to initialize {} storage; falling back to JSON", type, throwable);
            JsonGraveStorage json = new JsonGraveStorage(getDataFolder());
            json.init();
            return json;
        }
    }

    @NotNull
    private JdbcConfig h2Config(String tablePrefix) {
        File data = new File(getDataFolder(), "data");
        return new JdbcConfig(JdbcConfig.Type.H2,
                "jdbc:h2:file:" + data.getAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE", "", "", tablePrefix);
    }

    @NotNull
    private JdbcConfig sqliteConfig(String tablePrefix) {
        File data = new File(getDataFolder(), "data");
        return new JdbcConfig(JdbcConfig.Type.SQLITE, "jdbc:sqlite:" + data.getAbsolutePath(), "", "", tablePrefix);
    }

    @NotNull
    private JdbcConfig mysqlConfig(String tablePrefix) {
        String address = CONFIG.getString("storage.mysql.address", "127.0.0.1");
        int port = CONFIG.getInt("storage.mysql.port", 3306);
        String database = CONFIG.getString("storage.mysql.database", "axgraves");
        String username = CONFIG.getString("storage.mysql.username", "root");
        String password = CONFIG.getString("storage.mysql.password", "");
        String url = "jdbc:mysql://" + address + ':' + port + '/' + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        return new JdbcConfig(JdbcConfig.Type.MYSQL, url, username, password, tablePrefix);
    }

    private void restoreGrave(GraveRecord record) {
        Location location = LocationCodec.deserialize(record.location());
        if (location == null || location.getWorld() == null) {
            CloverLogger.warn("skipping a saved grave for {}; its world is not loaded", record.owner());
            return;
        }

        CloverScheduler.get().runAt(location, () -> {
            try {
                OfflinePlayer owner = Bukkit.getOfflinePlayer(record.owner());
                ItemStack[] items = ItemSerialization.deserialize(record.items());
                Grave grave = new Grave(location, owner, Arrays.asList(items), record.storedXP(), record.createdAt(), InventoryOrderSnapshot.EMPTY);
                grave.assignStorageId(record.id());
                SpawnedGraves.addGrave(grave);
            } catch (Exception ex) {
                CloverLogger.error("failed to restore a saved grave for {}", record.owner(), ex);
            }
        });
    }

    @Override
    public void onDisable() {
        SaveGraves.stop();
        GraveStorage storage = SpawnedGraves.storage();
        boolean persist = CONFIG != null && CONFIG.getBoolean("save-graves.enabled", true) && storage != null;

        for (Grave grave : SpawnedGraves.getGraves()) {
            if (!persist) {
                grave.remove(EndReason.SHUTDOWN);
                continue;
            }
            try {
                grave.contents().refreshSnapshot();
            } catch (Exception ex) {
                CloverLogger.error("failed to refresh a grave snapshot during shutdown", ex);
            }
            if (grave.getEntity() != null) grave.getEntity().remove();
            if (grave.getHologram() != null) grave.getHologram().remove();
        }

        if (persist) SaveGraves.flushDirty();
        if (storage != null) {
            try {
                storage.close();
            } catch (Exception ex) {
                CloverLogger.error("failed to close grave storage", ex);
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

        CloverScheduler.get().shutdown();
        instance = null;
    }
}
