package com.slyph.clovergraves.utils;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

import static com.slyph.clovergraves.AxGraves.CONFIG;

public class BlacklistUtils {
    public static boolean isBlacklisted(ItemStack item) {
        if (item == null) return false;
        ConfigurationSection section = CONFIG.getSection("blacklisted-items");
        if (section == null) return false;

        for (String key : section.getKeys(false)) {
            String base = "blacklisted-items." + key + '.';
            boolean banned = false;

            String materialName = CONFIG.getString(base + "material");
            if (materialName != null) {
                Material material = Material.getMaterial(materialName.toUpperCase(Locale.ROOT));
                if (material == null || item.getType() != material) continue;
                banned = true;
            }

            String nameContains = CONFIG.getString(base + "name-contains");
            if (nameContains != null) {
                ItemMeta meta = item.getItemMeta();
                if (meta == null || !meta.hasDisplayName() || !meta.getDisplayName().contains(nameContains)) continue;
                banned = true;
            }

            if (banned) return true;
        }
        return false;
    }
}
