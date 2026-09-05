package com.slyph.clovergraves.grave;

import com.artillexstudios.axapi.placeholders.PlaceholderHandler;
import com.artillexstudios.axapi.utils.StringUtils;
import com.slyph.clovergraves.utils.LimitUtils;
import org.bukkit.entity.Player;

import static com.slyph.clovergraves.AxGraves.CONFIG;

public class GravePlaceholders {
    private static int time;

    public static void reload() {
        time = CONFIG.getInt("despawn-time-seconds", 1800);
    }

    public static void register() {
        reload();

        String empty = "";
        PlaceholderHandler.register("player", handler -> {
            Grave grave = handler.raw(Grave.class);
            if (grave == null) return empty;
            return grave.getPlayerName();
        }, false);

        // %xp% and %item% used to read the grave's live state (storedXP / a Bukkit Inventory
        // scan) from whatever thread AxAPI's hologram tracker happens to update on - reading
        // GraveSnapshot instead means this never touches Bukkit/NMS objects off their owning
        // region, matching the rest of the threading fix (see GraveContents).
        PlaceholderHandler.register("xp", handler -> {
            Grave grave = handler.raw(Grave.class);
            if (grave == null) return empty;
            return String.valueOf(grave.snapshot().storedXP());
        }, false);

        PlaceholderHandler.register("item", handler -> {
            Grave grave = handler.raw(Grave.class);
            if (grave == null) return empty;
            return String.valueOf(grave.snapshot().itemCount());
        }, false);

        PlaceholderHandler.register("despawn-time", handler -> {
            Grave grave = handler.raw(Grave.class);
            if (grave == null) return empty;
            long spawned = grave.getSpawned();
            return StringUtils.formatTime(time != -1 ? (time * 1_000L - (System.currentTimeMillis() - spawned)) : System.currentTimeMillis() - spawned);
        }, false);

        PlaceholderHandler.register("grave_count", handler -> {
            return String.valueOf(SpawnedGraves.count());
        }, true);

        PlaceholderHandler.register("grave_limit", handler -> {
            Player player = handler.resolve(Player.class);
            if (player == null) return empty;
            int limit = LimitUtils.getGraveLimit(player);
            return String.valueOf(limit == -1 ? "∞" : limit);
        }, true);
    }
}
