package net.doodcraft.cozmyc.villages.listeners;

import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.managers.VillageManager;
import net.doodcraft.cozmyc.villages.models.ChunkCoord;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;

import java.util.HashSet;
import java.util.Set;

public class VillageMonsterListener implements Listener {
    private final VillageManager villageManager;
    private final Set<Entity> spawnerMobs = new HashSet<>();
    private final Set<Entity> spawnEggMobs = new HashSet<>();
    private final VillagesPlugin plugin;

    public VillageMonsterListener(VillageManager villageManager, VillagesPlugin plugin) {
        this.villageManager = villageManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Monster monster) {
            boolean allowSpawner = plugin.getConfig().getBoolean("villages.mobs.allow_spawner_mob_spawns", true);
            boolean allowEgg = plugin.getConfig().getBoolean("villages.mobs.allow_egg_mob_spawns", true);
            boolean allowNatural = plugin.getConfig().getBoolean("villages.mobs.allow_natural_mob_spawns", false);
            // Track spawner mobs
            if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER) {
                if (!allowSpawner) {
                    event.setCancelled(true);
                    return;
                }
                spawnerMobs.add(monster);
            }
            // Track spawn egg mobs
            else if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.EGG || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.DISPENSE_EGG) {
                if (!allowEgg) {
                    event.setCancelled(true);
                    return;
                }
                spawnEggMobs.add(monster);
            }
            // Cancel natural spawns in claimed chunks
            else if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
                ChunkCoord chunk = new ChunkCoord(event.getLocation().getWorld().getName(), 
                    event.getLocation().getChunk().getX(), 
                    event.getLocation().getChunk().getZ());
                if (villageManager.isChunkClaimed(chunk) && !allowNatural) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        boolean removeOnPlayerDamage = plugin.getConfig().getBoolean("villages.mobs.remove_mobs_on_player_damage", true);
        if (event.getDamager() instanceof Monster monster) {
            // Skip if it's a spawner or spawn egg mob
            if (spawnerMobs.contains(monster) || spawnEggMobs.contains(monster)) {
                return;
            }

            Location loc = event.getEntity().getLocation();
            ChunkCoord chunk = new ChunkCoord(loc.getWorld().getName(), loc.getChunk().getX(), loc.getChunk().getZ());
            if (villageManager.isChunkClaimed(chunk)) {
                event.setCancelled(true);
                if (removeOnPlayerDamage) {
                    monster.remove();
                }
            }
        }

        if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Monster monster) {
                // Skip if it's a spawner or spawn egg mob
                if (spawnerMobs.contains(monster) || spawnEggMobs.contains(monster)) {
                    return;
                }

                Location loc = event.getEntity().getLocation();
                ChunkCoord chunk = new ChunkCoord(loc.getWorld().getName(), loc.getChunk().getX(), loc.getChunk().getZ());
                if (villageManager.isChunkClaimed(chunk)) {
                    event.setCancelled(true);
                    if (removeOnPlayerDamage) {
                        monster.remove();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        spawnerMobs.remove(entity);
        spawnEggMobs.remove(entity);
    }

    @EventHandler
    public void onEntityRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        spawnerMobs.remove(entity);
        spawnEggMobs.remove(entity);
    }
} 