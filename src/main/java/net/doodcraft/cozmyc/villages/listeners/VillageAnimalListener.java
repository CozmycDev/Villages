package net.doodcraft.cozmyc.villages.listeners;

import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.managers.VillageManager;
import net.doodcraft.cozmyc.villages.models.ChunkCoord;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class VillageAnimalListener implements Listener {
    private final VillageManager villageManager;
    private final VillagesPlugin plugin;

    public VillageAnimalListener(VillageManager villageManager, VillagesPlugin plugin) {
        this.villageManager = villageManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onAnimalDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("villages.mobs.protect_animals_in_claims", true)) {
            return;
        }
        Entity victim = event.getEntity();
        
        // Only protect tameable animals
        if (!(victim instanceof Animals) && !(victim instanceof Tameable)) {
            return;
        }

        ChunkCoord chunk = new ChunkCoord(victim.getWorld().getName(), 
            victim.getLocation().getChunk().getX(), 
            victim.getLocation().getChunk().getZ());

        // Check if the chunk is claimed
        if (!villageManager.isChunkClaimed(chunk)) {
            return;
        }

        // Get the attacker
        Entity attacker = event.getDamager();
        
        // If the attacker is a player
        if (attacker instanceof Player player) {
            // Check if the player is in the village that owns this chunk
            String villageName = villageManager.getVillageNameByChunk(chunk);
            if (villageName == null) return;
            
            if (!villageManager.getVillageByName(villageName).getMembers().contains(player.getUniqueId())) {
                // Player is not a member of the village, cancel the damage
                event.setCancelled(true);
                player.sendMessage("§cYou cannot harm animals in this village's territory.");
            }
        }
        // If the attacker is a monster
        else if (attacker instanceof Monster) {
            // Cancel damage from monsters
            event.setCancelled(true);
        }
        // If the attacker is a projectile
        else if (attacker instanceof Projectile projectile) {
            // Check if the projectile was shot by a player
            if (projectile.getShooter() instanceof Player player) {
                String villageName = villageManager.getVillageNameByChunk(chunk);
                if (villageName == null) return;
                
                if (!villageManager.getVillageByName(villageName).getMembers().contains(player.getUniqueId())) {
                    // Player is not a member of the village, cancel the damage
                    event.setCancelled(true);
                    player.sendMessage("§cYou cannot harm animals in this village's territory.");
                }
            } else {
                // Cancel damage from non-player projectiles (like skeleton arrows)
                event.setCancelled(true);
            }
        }
    }
} 