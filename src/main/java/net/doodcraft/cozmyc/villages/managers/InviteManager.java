package net.doodcraft.cozmyc.villages.managers;

import net.doodcraft.cozmyc.villages.VillagesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InviteManager {
    private final Map<UUID, Map<String, InviteData>> invites = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> inviteTasks = new ConcurrentHashMap<>();
    private final VillagesPlugin plugin;

    public InviteManager(VillagesPlugin plugin) {
        this.plugin = plugin;
    }

    public void addInvite(UUID player, String village, UUID inviter) {
        Map<String, InviteData> playerInvites = invites.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        playerInvites.put(village, new InviteData(inviter, System.currentTimeMillis()));

        // Cancel existing task if any
        BukkitTask existingTask = inviteTasks.remove(player);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // Schedule new task to remove invite after timeout
        int timeout = plugin.getConfig().getInt("village.invite_timeout_seconds", 180);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeInvite(player, village);
        }, timeout * 20L);
        inviteTasks.put(player, task);
    }

    public void removeInvite(UUID player, String village) {
        Map<String, InviteData> playerInvites = invites.get(player);
        if (playerInvites != null) {
            playerInvites.remove(village);
            if (playerInvites.isEmpty()) {
                invites.remove(player);
                BukkitTask task = inviteTasks.remove(player);
                if (task != null) {
                    task.cancel();
                }
            }
        }
    }

    public void removeAllInvites(UUID player) {
        invites.remove(player);
        BukkitTask task = inviteTasks.remove(player);
        if (task != null) {
            task.cancel();
        }
    }

    public boolean hasInvite(UUID player) {
        Map<String, InviteData> playerInvites = invites.get(player);
        return playerInvites != null && !playerInvites.isEmpty();
    }

    public Set<String> getPendingInvites(UUID player) {
        Map<String, InviteData> playerInvites = invites.get(player);
        return playerInvites != null ? new HashSet<>(playerInvites.keySet()) : Collections.emptySet();
    }

    public UUID getInviter(UUID player, String village) {
        Map<String, InviteData> playerInvites = invites.get(player);
        if (playerInvites != null) {
            InviteData data = playerInvites.get(village);
            return data != null ? data.inviter : null;
        }
        return null;
    }

    private static class InviteData {
        final UUID inviter;
        final long timestamp;

        InviteData(UUID inviter, long timestamp) {
            this.inviter = inviter;
            this.timestamp = timestamp;
        }
    }
} 