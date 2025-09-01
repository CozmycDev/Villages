package net.doodcraft.cozmyc.villages.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.managers.VillageManager;
import net.doodcraft.cozmyc.villages.models.Claim;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

@Deprecated
public class VillageProtectionListener implements Listener {
    private final VillagesPlugin plugin;
    private final VillageManager villageManager;
    private final String separator;

    public VillageProtectionListener(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.villageManager = plugin.getVillageManager();
        this.separator = "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    }

    private boolean hasPermission(Player player, String regionId) {
        String[] parts = regionId.split("_");
        if (parts.length < 4) return false;

        StringBuilder villageNameBuilder = new StringBuilder();
        for (int i = 1; i < parts.length - 2; i++) {
            if (i > 1) villageNameBuilder.append("_");
            villageNameBuilder.append(parts[i]);
        }
        String villageName = villageNameBuilder.toString();

        int x = Integer.parseInt(parts[parts.length - 2]);
        int z = Integer.parseInt(parts[parts.length - 1]);

        Claim claim = plugin.getClaimManager().getClaim(new net.doodcraft.cozmyc.villages.models.ChunkCoord(
            player.getWorld().getName(), x, z));
            
        if (claim == null) return false;
        
        // If the chunk is assigned to a specific player
        if (claim.getOwner() != null) {
            // Owner can always build
            if (claim.getOwner().equals(player.getUniqueId())) return true;
            
            // Check if player is in allowed members list
            return claim.getAllowedMembers().contains(player.getUniqueId());
        }
        
        // For village-owned chunks, check if player is a member and if members are allowed to edit
        return villageManager.getVillageByName(villageName).getMembers().contains(player.getUniqueId()) &&
               villageManager.getVillageByName(villageName).isAllowMembersToEditUnassignedChunks();
    }

    private String getRegionIdAtLocation(org.bukkit.Location location) {
        // Convert Bukkit Location to WorldEdit Location
        com.sk89q.worldedit.util.Location weLocation = BukkitAdapter.adapt(location);
        
        // Get the region container and query
        com.sk89q.worldguard.protection.regions.RegionContainer container = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
        com.sk89q.worldguard.protection.regions.RegionQuery query = container.createQuery();
        
        // Get applicable regions and find the first village region
        ApplicableRegionSet regions = query.getApplicableRegions(weLocation);
        return regions.getRegions().stream()
            .filter(r -> r.getId().startsWith("village_"))
            .findFirst()
            .map(r -> r.getId())
            .orElse(null);
    }

    // No longer used, using region flag instead.
    private void sendDeniedMessage(Player player, String type, String regionId) {
        String message = "§c§l              ⚠ " + type + " DENIED";
        player.sendMessage(separator);
        player.sendMessage(message);
        player.sendMessage(separator);
        player.sendMessage("");
        player.sendMessage("§7§o    You don't have permission to " + 
            (type.equals("BUILD") ? "build" : 
             type.equals("USE") ? "use items" : "interact") + 
            " in this village.");
        player.sendMessage("§7§o    Village: §f" + regionId.replace('_', ' '));
        player.sendMessage("");
        player.sendMessage(separator);
    }
} 