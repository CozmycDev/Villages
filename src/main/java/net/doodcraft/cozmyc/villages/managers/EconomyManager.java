package net.doodcraft.cozmyc.villages.managers;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

public class EconomyManager {
    private static Economy economy;
    public static void setEconomy(Economy econ) { economy = econ; }
    public static boolean hasBalance(Player player, double amount) {
        return economy.getBalance(player) >= amount;
    }
    public static boolean hasBalance(UUID uuid, double amount) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        return economy.getBalance(p) >= amount;
    }
    public static void withdraw(Player player, double amount) {
        economy.withdrawPlayer(player, amount);
    }
    public static void withdraw(UUID uuid, double amount) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        economy.withdrawPlayer(p, amount);
    }
    public static void deposit(Player player, double amount) {
        economy.depositPlayer(player, amount);
    }
    public static void deposit(UUID uuid, double amount) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        economy.depositPlayer(p, amount);
    }
    public static double getBalance(Player player) {
        return economy.getBalance(player);
    }
    public static double getBalance(UUID uuid) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        return economy.getBalance(p);
    }
    public static String getName() {
        return economy.currencyNamePlural();
    }
} 