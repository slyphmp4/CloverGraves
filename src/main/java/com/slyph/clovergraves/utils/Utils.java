package com.slyph.clovergraves.utils;

import com.artillexstudios.axapi.nms.wrapper.ServerPlayerWrapper;
import com.artillexstudios.axapi.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import static com.slyph.clovergraves.AxGraves.CONFIG;

public class Utils {

    @NotNull
    public static ItemStack getPlayerHead(@NotNull OfflinePlayer player) {
        ItemBuilder builder = ItemBuilder.create(Material.PLAYER_HEAD);

        String texture = null;
        if (CONFIG.getBoolean("custom-grave-skull.enabled", false)) {
            texture = CONFIG.getString("custom-grave-skull.base64");
        } else if (player.getPlayer() != null) {
            ServerPlayerWrapper wrapper = ServerPlayerWrapper.wrap(player);
            texture = wrapper.textures().texture();
        }

        if (texture != null) builder.setTextureValue(texture);

        return builder.get();
    }

    public static boolean isHelmet(Material material) {
        return switch (material.name()) {
            case "LEATHER_HELMET", "CHAINMAIL_HELMET", "IRON_HELMET", "COPPER_HELMET", "GOLDEN_HELMET",
                 "DIAMOND_HELMET", "NETHERITE_HELMET", "TURTLE_HELMET" -> true;
            default -> false;
        };
    }

    public static boolean isChestplate(Material material) {
        return switch (material.name()) {
            case "LEATHER_CHESTPLATE", "CHAINMAIL_CHESTPLATE", "IRON_CHESTPLATE", "COPPER_CHESTPLATE", "GOLDEN_CHESTPLATE",
                 "DIAMOND_CHESTPLATE", "NETHERITE_CHESTPLATE", "ELYTRA" -> true;
            default -> false;
        };
    }

    public static boolean isLeggings(Material material) {
        return switch (material.name()) {
            case "LEATHER_LEGGINGS", "CHAINMAIL_LEGGINGS", "IRON_LEGGINGS", "COPPER_LEGGINGS", "GOLDEN_LEGGINGS",
                 "DIAMOND_LEGGINGS", "NETHERITE_LEGGINGS" -> true;
            default -> false;
        };
    }

    public static boolean isBoots(Material material) {
        return switch (material.name()) {
            case "LEATHER_BOOTS", "CHAINMAIL_BOOTS", "IRON_BOOTS", "COPPER_BOOTS", "GOLDEN_BOOTS",
                 "DIAMOND_BOOTS", "NETHERITE_BOOTS" -> true;
            default -> false;
        };
    }
}
