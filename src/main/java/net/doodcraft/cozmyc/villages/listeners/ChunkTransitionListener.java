package net.doodcraft.cozmyc.villages.listeners;

import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.managers.VillageManager;
import net.doodcraft.cozmyc.villages.models.ChunkCoord;
import org.bukkit.ChatColor;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class ChunkTransitionListener implements Listener {
    private final VillagesPlugin plugin;
    private final VillageManager villageManager;

    public ChunkTransitionListener(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.villageManager = plugin.getVillageManager();
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent event) {
        ChunkCoord chunk = new ChunkCoord(event.getLocation().getChunk());
        if (event.getEntity() == null || event.getEntity().isDead() || !event.getEntity().isValid()) return;
        if (!(event.getEntity() instanceof Monster)) return;
        if (villageManager.isChunkClaimed(chunk)) {
            event.getEntity().remove();
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) {
            return;
        }

        Player player = event.getPlayer();
        ChunkCoord fromChunk = new ChunkCoord(event.getFrom().getChunk());
        ChunkCoord toChunk = new ChunkCoord(event.getTo().getChunk());

        String fromVillageName = villageManager.getVillageNameByChunk(fromChunk);
        String toVillageName = villageManager.getVillageNameByChunk(toChunk);

        if (toVillageName != null && fromVillageName == null) {
            player.sendTitle(
                    ChatColor.GOLD + "Now entering..",
                    ChatColor.AQUA + toVillageName.replaceAll("_", " "),
                    10, 40, 10
            );
        } else if (toVillageName == null && fromVillageName != null) {
            player.sendTitle(
                    ChatColor.GRAY + "Now leaving..",
                    ChatColor.AQUA + fromVillageName.replaceAll("_", " "),
                    10, 40, 10
            );
        }
    }
} 