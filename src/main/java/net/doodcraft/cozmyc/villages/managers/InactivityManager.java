package net.doodcraft.cozmyc.villages.managers;

import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.models.Claim;
import net.doodcraft.cozmyc.villages.models.Village;
import net.doodcraft.cozmyc.villages.utils.WGUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class InactivityManager {
    private final VillagesPlugin plugin;
    private final VillageManager villageManager;
    private final long inactivityMillis;

    public InactivityManager(VillagesPlugin plugin, VillageManager villageManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;
        long days = plugin.getConfig().getLong("village.mayor_inactivity_days", 360);
        if (days < 0) {
            this.inactivityMillis = -1;
        } else {
            this.inactivityMillis = days * 24L * 60 * 60 * 1000;
        }
    }

    public void runCheck() {
        long now = System.currentTimeMillis();
        if (this.inactivityMillis <= 0) {
            return;
        }
        if (this.inactivityMillis == -1) {
            // Inactivity check disabled
            return;
        }
        for (Village v : villageManager.getAllVillages()) {
            if (now - v.getLastMayorLogin() > inactivityMillis) {
                for (Claim claim : v.getClaims()) {
                    String regionId = "village_" + v.getName().toLowerCase() + "_" + claim.getChunk().getX() + "_" + claim.getChunk().getZ();
                    World world = Bukkit.getWorld(claim.getChunk().getWorld());
                    if (world != null) {
                        WGUtils.deleteRegion(world, regionId);
                    }
                }
                // Notify all members if online
                for (java.util.UUID member : v.getMembers()) {
                    Player p = Bukkit.getPlayer(member);
                    if (p != null && p.isOnline()) {
                        p.sendMessage("§cYour village '" + v.getName() + "' has expired due to mayor inactivity.");
                    }
                }
                villageManager.deleteVillageAndRefund(v.getName());
                plugin.getLogger().info("Village '" + v.getName() + "' deleted due to mayor inactivity.");
            }
        }
    }
} 