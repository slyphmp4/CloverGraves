package com.slyph.clovergraves.utils;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalInt;

import static com.slyph.clovergraves.AxGraves.CONFIG;

public class LimitUtils {
    private static final String PREFIX = "axgraves.limit.";

    public static int getGraveLimit(Player player) {
        int amount = 0;
        boolean has = false;

        for (PermissionAttachmentInfo permissionInfo : player.getEffectivePermissions()) {
            if (!permissionInfo.getValue()) continue;

            OptionalInt value = parseLimitNode(permissionInfo.getPermission());
            if (value.isEmpty()) continue;

            amount = Math.max(amount, value.getAsInt());
            has = true;
        }

        if (!has) return CONFIG.getInt("grave-limit", -1);
        return amount;
    }

    @NotNull
    public static OptionalInt parseLimitNode(@NotNull String permission) {
        if (!permission.startsWith(PREFIX)) return OptionalInt.empty();

        String suffix = permission.substring(PREFIX.length());
        try {
            return OptionalInt.of(Integer.parseInt(suffix));
        } catch (NumberFormatException ex) {
            CloverLogger.warn("invalid grave-limit permission node '{}', ignoring", permission);
            return OptionalInt.empty();
        }
    }
}
