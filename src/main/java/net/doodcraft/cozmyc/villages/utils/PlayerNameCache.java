package net.doodcraft.cozmyc.villages.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerNameCache {
    private static final Map<UUID, String> nameCache = new ConcurrentHashMap<>();

    public static String getName(UUID uuid) {
        return nameCache.computeIfAbsent(uuid, k -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(k);
            return player.getName() != null ? player.getName() : "Unknown Player";
        });
    }

    public static void clearCache() {
        nameCache.clear();
    }

    public static void removeFromCache(UUID uuid) {
        nameCache.remove(uuid);
    }
} 