package com.artillexstudios.axgraves.utils;

import com.artillexstudios.axapi.utils.logging.LogUtils;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalInt;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;

public class LimitUtils {
    private static final String PREFIX = "axgraves.limit.";

    public static int getGraveLimit(Player player) {
        int am = 0;
        boolean has = false;

        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            if (!pai.getValue()) continue;

            OptionalInt value = parseLimitNode(pai.getPermission());
            if (value.isEmpty()) continue;

            am = Math.max(am, value.getAsInt());
            has = true;
        }

        if (!has) return CONFIG.getInt("grave-limit", -1);
        return am;
    }

    /**
     * Parses the numeric suffix of an {@code axgraves.limit.<n>} node. Permission plugins
     * routinely grant wildcard nodes like {@code axgraves.limit.*} (or malformed children like
     * {@code axgraves.limit.5.foo}) - the previous unguarded {@code Integer.parseInt} threw on
     * either, and that exception propagated out of the unprotected death path.
     */
    @NotNull
    public static OptionalInt parseLimitNode(@NotNull String permission) {
        if (!permission.startsWith(PREFIX)) return OptionalInt.empty();

        String suffix = permission.substring(PREFIX.length());
        try {
            return OptionalInt.of(Integer.parseInt(suffix));
        } catch (NumberFormatException ex) {
            LogUtils.warn("invalid grave-limit permission node '{}', ignoring", permission);
            return OptionalInt.empty();
        }
    }
}
