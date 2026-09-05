package com.slyph.clovergraves.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.slyph.clovergraves.AxGraves.CONFIG;

public class Utils {
    private static final Pattern TEXTURE_URL = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    @NotNull
    public static ItemStack getPlayerHead(@NotNull OfflinePlayer player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (CONFIG.getBoolean("custom-grave-skull.enabled", false)) {
            String base64 = CONFIG.getString("custom-grave-skull.base64");
            if (applyBase64Texture(meta, base64)) {
                head.setItemMeta(meta);
                return head;
            }
        }

        meta.setOwningPlayer(player);
        head.setItemMeta(meta);
        return head;
    }

    private static boolean applyBase64Texture(@NotNull SkullMeta meta, String base64) {
        if (base64 == null || base64.isBlank()) return false;
        try {
            String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            Matcher matcher = TEXTURE_URL.matcher(json);
            if (!matcher.find()) return false;

            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create(matcher.group(1)).toURL());
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
            return true;
        } catch (Exception ignored) {
            return false;
        }
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
