package com.slyph.clovergraves.utils;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EconomyHook {
    private static Economy economy;
    private static boolean lookedUp;
    private static boolean warnedMissing;

    private EconomyHook() {
    }

    @Nullable
    private static Economy get() {
        if (!lookedUp) {
            lookedUp = true;
            RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
            economy = provider == null ? null : provider.getProvider();
        }
        return economy;
    }

    public static boolean isAvailable() {
        return get() != null;
    }

    public static boolean has(@NotNull OfflinePlayer player, double amount) {
        Economy provider = get();
        if (provider == null) {
            warnMissingOnce();
            return true;
        }
        return provider.has(player, amount);
    }

    public static boolean withdraw(@NotNull OfflinePlayer player, double amount) {
        Economy provider = get();
        if (provider == null) {
            warnMissingOnce();
            return true;
        }
        EconomyResponse response = provider.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    public static double balance(@NotNull OfflinePlayer player) {
        Economy provider = get();
        return provider == null ? 0 : provider.getBalance(player);
    }

    @NotNull
    public static String format(double amount, @NotNull String symbol) {
        if (amount == Math.floor(amount) && !Double.isInfinite(amount)) return symbol + (long) amount;
        return symbol + String.format(java.util.Locale.ROOT, "%.2f", amount);
    }

    public static void reset() {
        economy = null;
        lookedUp = false;
        warnedMissing = false;
    }

    private static void warnMissingOnce() {
        if (warnedMissing) return;
        warnedMissing = true;
        CloverLogger.warn("teleport.cost is set but no Vault-compatible economy provider is available; grave teleports are free");
    }
}
