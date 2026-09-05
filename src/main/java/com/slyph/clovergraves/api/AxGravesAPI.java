package com.slyph.clovergraves.api;

import com.slyph.clovergraves.utils.LimitUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AxGravesAPI {

    public static int getGraveLimit(@NotNull Player player) {
        return LimitUtils.getGraveLimit(player);
    }
}
