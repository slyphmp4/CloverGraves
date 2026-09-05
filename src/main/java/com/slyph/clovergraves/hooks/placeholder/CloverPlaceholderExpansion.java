package com.slyph.clovergraves.hooks.placeholder;

import com.slyph.clovergraves.AxGraves;
import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.utils.LimitUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CloverPlaceholderExpansion extends PlaceholderExpansion {
    private final String identifier;

    public CloverPlaceholderExpansion(@NotNull String identifier) {
        this.identifier = identifier;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return identifier;
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "slyph";
    }

    @Override
    @NotNull
    public String getVersion() {
        return AxGraves.getInstance().getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    @Nullable
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        return switch (params.toLowerCase()) {
            case "grave_count" -> String.valueOf(SpawnedGraves.count());
            case "grave_limit" -> {
                if (player == null) yield "";
                int limit = LimitUtils.getGraveLimit(player);
                yield limit == -1 ? "∞" : String.valueOf(limit);
            }
            default -> null;
        };
    }
}
