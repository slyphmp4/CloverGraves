package com.slyph.clovergraves.hooks.placeholder;

import org.bukkit.Bukkit;

public final class PlaceholderHook {
    private static boolean registered;

    private PlaceholderHook() {
    }

    public static void register() {
        if (registered) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;

        new CloverPlaceholderExpansion("clovergraves").register();
        new CloverPlaceholderExpansion("axgraves").register();
        registered = true;
    }
}
