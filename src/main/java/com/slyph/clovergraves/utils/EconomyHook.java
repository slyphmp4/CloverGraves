package com.slyph.clovergraves.utils;

import com.artillexstudios.axapi.utils.logging.LogUtils;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thin wrapper around Vault's {@link Economy} service, used for the {@code teleport.cost} charge.
 * Vault is a soft dependency ({@code softdepend: [Vault]} in plugin.yml) - if it or an economy
 * plugin isn't installed, every method here degrades to "there is no cost" rather than blocking
 * the teleport feature entirely, since a misconfigured/missing economy plugin shouldn't lock
 * players out of a core feature. A single warning is logged the first time that happens, not on
 * every attempt.
 */
public final class EconomyHook {
    private EconomyHook() {
    }

    private static Economy economy;
    private static boolean lookedUp = false;
    private static boolean warnedMissing = false;

    @Nullable
    private static Economy get() {
        if (!lookedUp) {
            lookedUp = true;
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            economy = rsp == null ? null : rsp.getProvider();
        }
        return economy;
    }

    public static boolean isAvailable() {
        return get() != null;
    }

    public static boolean has(@NotNull OfflinePlayer player, double amount) {
        Economy econ = get();
        if (econ == null) {
            warnMissingOnce();
            return true;
        }
        return econ.has(player, amount);
    }

    /** @return true if the withdrawal succeeded (or there is no economy to charge against) */
    public static boolean withdraw(@NotNull OfflinePlayer player, double amount) {
        Economy econ = get();
        if (econ == null) {
            warnMissingOnce();
            return true;
        }
        EconomyResponse response = econ.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    public static double balance(@NotNull OfflinePlayer player) {
        Economy econ = get();
        return econ == null ? 0 : econ.getBalance(player);
    }

    /** Formats {@code amount} with the configured symbol - not Vault's own format(), so the
     *  displayed currency matches teleport.currency-symbol regardless of what economy plugin is
     *  installed. */
    @NotNull
    public static String format(double amount, @NotNull String symbol) {
        if (amount == Math.floor(amount) && !Double.isInfinite(amount)) {
            return symbol + (long) amount;
        }
        return symbol + String.format("%.2f", amount);
    }

    private static void warnMissingOnce() {
        if (warnedMissing) return;
        warnedMissing = true;
        LogUtils.warn("teleport.cost is set but no Vault-compatible economy plugin is installed - grave teleports are free until one is added");
    }
}
