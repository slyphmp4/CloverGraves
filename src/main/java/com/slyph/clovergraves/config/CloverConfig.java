package com.slyph.clovergraves.config;

import com.slyph.clovergraves.utils.CloverLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class CloverConfig {
    private final JavaPlugin plugin;
    private final String resourceName;
    private final File file;
    private volatile YamlConfiguration configuration;

    public CloverConfig(@NotNull JavaPlugin plugin, @NotNull String resourceName) {
        this.plugin = plugin;
        this.resourceName = resourceName;
        this.file = new File(plugin.getDataFolder(), resourceName);

        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
        }

        if (!reload()) {
            throw new IllegalStateException("Failed to load " + resourceName);
        }
    }

    public boolean reload() {
        try {
            YamlConfiguration loaded = new YamlConfiguration();
            loaded.load(file);

            try (InputStream input = plugin.getResource(resourceName)) {
                if (input != null) {
                    YamlConfiguration defaults = new YamlConfiguration();
                    try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                        defaults.load(reader);
                    }
                    loaded.setDefaults(defaults);
                }
            }

            configuration = loaded;
            return true;
        } catch (Exception ex) {
            CloverLogger.error("failed to load {}", resourceName, ex);
            return false;
        }
    }

    public boolean getBoolean(@NotNull String path, boolean def) {
        Object value = configuration.get(path);
        return value == null ? def : configuration.getBoolean(path);
    }

    public int getInt(@NotNull String path, int def) {
        Object value = configuration.get(path);
        return value == null ? def : configuration.getInt(path);
    }

    public float getFloat(@NotNull String path, float def) {
        Object value = configuration.get(path);
        return value == null ? def : (float) configuration.getDouble(path);
    }

    public double getDouble(@NotNull String path, double def) {
        Object value = configuration.get(path);
        return value == null ? def : configuration.getDouble(path);
    }

    @Nullable
    public String getString(@NotNull String path) {
        return configuration.getString(path);
    }

    @NotNull
    public String getString(@NotNull String path, @NotNull String def) {
        String value = configuration.getString(path);
        return value == null ? def : value;
    }

    @NotNull
    public List<String> getStringList(@NotNull String path) {
        return List.copyOf(configuration.getStringList(path));
    }

    @NotNull
    public List<String> getLines(@NotNull String path) {
        Object raw = configuration.get(path);
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object value : list) {
                if (value != null) result.add(String.valueOf(value));
            }
            return result;
        }

        String value = configuration.getString(path);
        return value == null ? List.of() : List.of(value);
    }

    @NotNull
    public String getFirstLine(@NotNull String path, @NotNull String def) {
        List<String> lines = getLines(path);
        if (lines.isEmpty()) return def;
        for (String line : lines) {
            if (!line.equalsIgnoreCase("&7")) return line;
        }
        return lines.getFirst();
    }

    @Nullable
    public ConfigurationSection getSection(@NotNull String path) {
        return configuration.getConfigurationSection(path);
    }
}
