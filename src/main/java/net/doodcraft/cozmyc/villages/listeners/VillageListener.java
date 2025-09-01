package net.doodcraft.cozmyc.villages.listeners;

import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.managers.VillageManager;
import net.doodcraft.cozmyc.villages.models.ChunkCoord;
import net.doodcraft.cozmyc.villages.models.Village;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class VillageListener implements Listener {
    private final VillagesPlugin plugin;
    private final VillageManager villageManager;
    public VillageListener(VillagesPlugin plugin, VillageManager villageManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String vName = villageManager.getVillageNameByPlayer(uuid);
        if (vName != null) {
            Village village = villageManager.getVillageByName(vName);
            if (village != null && village.getMayor().equals(uuid)) {
                village.setLastMayorLogin(System.currentTimeMillis());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block block = event.getBlock();
        Block toBlock = event.getToBlock();

        // Check if either block is liquid
        if (!block.isLiquid() && !toBlock.isLiquid()) {
            return;
        }

        ChunkCoord fromChunk = new ChunkCoord(block.getWorld().getName(), block.getChunk().getX(), block.getChunk().getZ());
        ChunkCoord toChunk = new ChunkCoord(toBlock.getWorld().getName(), toBlock.getChunk().getX(), toBlock.getChunk().getZ());

        // If the chunks are the same, no need to check
        if (fromChunk.equals(toChunk)) {
            return;
        }

        // Get village names for both chunks
        String fromVillageName = plugin.getVillageManager().getVillageNameByChunk(fromChunk);
        String toVillageName = plugin.getVillageManager().getVillageNameByChunk(toChunk);

        //plugin.getLogger().info("[Liquid Flow] From: " + fromChunk + " (" + fromVillageName + ") To: " + toChunk + " (" + toVillageName + ")");

        // If the destination chunk is claimed but the source isn't, prevent flow
        if (toVillageName != null && fromVillageName == null) {
            //plugin.getLogger().info("[Liquid Flow] Cancelled: Destination claimed but source unclaimed");
            event.setCancelled(true);
            return;
        }

        // If both chunks are claimed but by different villages, prevent flow
        if (toVillageName != null && fromVillageName != null && !toVillageName.equals(fromVillageName)) {
            //plugin.getLogger().info("[Liquid Flow] Cancelled: Different villages");
            event.setCancelled(true);
            return;
        }

//        plugin.getLogger().info("[Liquid Flow] Allowed: " +
//                (fromVillageName != null && toVillageName == null ? "Flowing out of claim" :
//                        fromVillageName != null && toVillageName != null ? "Same village" :
//                                "Both unclaimed"));
    }
} 