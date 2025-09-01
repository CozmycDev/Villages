package net.doodcraft.cozmyc.villages.listeners;

import com.projectkorra.projectkorra.Element;
import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.managers.VillageManager;
import net.doodcraft.cozmyc.villages.models.Claim;
import net.doodcraft.cozmyc.villages.models.Village;
import net.doodcraft.cozmyc.villages.utils.PKBridge;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ElementBuffZoneListener implements Listener {
    private final VillagesPlugin plugin;
    private final VillageManager villageManager;
    private final PKBridge pkBridge;
    private final Map<UUID, Boolean> inBuffZone = new HashMap<>();
    private final Map<UUID, BukkitTask> buffZoneParticleTasks = new HashMap<>();

    public ElementBuffZoneListener(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.villageManager = plugin.getVillageManager();
        this.pkBridge = plugin.getPKBridge();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        boolean wasInZone = inBuffZone.getOrDefault(uuid, false);
        boolean isInZone = isInElementBuffZone(event);
        String villageElement = villageManager.getVillageElementByPlayer(player.getUniqueId());
        if (villageElement == null) {
            inBuffZone.put(uuid, false);
            return;
        }
        String playerElement = null;
        try {
            Element pkElement = com.projectkorra.projectkorra.BendingPlayer.getBendingPlayer(player).getElements().stream().findFirst().orElse(null);
            if (pkElement != null) playerElement = pkElement.getName();
        } catch (Throwable ignored) {}
        if (playerElement == null || !playerElement.equalsIgnoreCase(villageElement)) {
            inBuffZone.put(uuid, false);
            return;
        }
        if (wasInZone != isInZone) {
            inBuffZone.put(uuid, isInZone);
            FileConfiguration config = plugin.getConfig();
            double percent = getBuffPercent(player);
            String prefix = plugin.getConfig().getString("messages.prefix", "");
            boolean showTitles = config.getBoolean("projectkorra.element_buff_zone.show_titles", true);
            boolean showMessages = config.getBoolean("projectkorra.element_buff_zone.show_messages", false);
            String enterMessage = parseColor(config.getString("projectkorra.element_buff_zone.enter_message", "&bElement Buff! &eYou are empowered by your village's element. &a+%percent% damage").replace("%percent%", String.valueOf((int)percent)));
            String exitMessage = parseColor(config.getString("projectkorra.element_buff_zone.exit_message", "&7Buff Faded &eYou have left your element's zone."));
            // Cancel any previous particle task
            BukkitTask prevTask = buffZoneParticleTasks.remove(uuid);
            if (prevTask != null) prevTask.cancel();
            if (isInZone) {
                if (showTitles) {
                    String title = parseColor(config.getString("projectkorra.element_buff_zone.enter_title", "&bElement Buff!"));
                    String subtitle = parseColor(config.getString("projectkorra.element_buff_zone.enter_subtitle", "&eYou are empowered by your village's element. &a+%percent% damage").replace("%percent%", String.valueOf((int)percent)));
                    player.sendTitle(title, subtitle, 10, 40, 10);
                }
                if (showMessages) {
                    player.sendMessage(prefix + enterMessage);
                }
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.2f);
                BukkitTask particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!player.isOnline() || player.isDead()) {
                        BukkitTask t = buffZoneParticleTasks.remove(uuid);
                        if (t != null) t.cancel();
                        return;
                    }
                    Location loc = player.getLocation();
                    for (int i = 0; i < 16; i++) {
                        double angle = 2 * Math.PI * i / 16;
                        double x = loc.getX() + Math.cos(angle) * 1.5;
                        double z = loc.getZ() + Math.sin(angle) * 1.5;
                        Location particleLoc = new Location(loc.getWorld(), x, loc.getY() + 1, z);
                        player.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, particleLoc, 1, 0.5, 0.5, 0.5, 0.02);
                    }
                }, 0L, 5L);
                buffZoneParticleTasks.put(uuid, particleTask);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    BukkitTask t = buffZoneParticleTasks.remove(uuid);
                    if (t != null) t.cancel();
                }, 40L);
            } else {
                if (showTitles) {
                    String title = parseColor(config.getString("projectkorra.element_buff_zone.exit_title", "&7Buff Faded"));
                    String subtitle = parseColor(config.getString("projectkorra.element_buff_zone.exit_subtitle", "&eYou have left your element's zone."));
                    player.sendTitle(title, subtitle, 10, 40, 10);
                }
                if (showMessages) {
                    player.sendMessage(prefix + exitMessage);
                }
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.1f);
                BukkitTask particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!player.isOnline() || player.isDead()) {
                        BukkitTask t = buffZoneParticleTasks.remove(uuid);
                        if (t != null) t.cancel();
                        return;
                    }
                    Location loc = player.getLocation();
                    for (int i = 0; i < 16; i++) {
                        double angle = 2 * Math.PI * i / 16;
                        double x = loc.getX() + Math.cos(angle) * 1.5;
                        double z = loc.getZ() + Math.sin(angle) * 1.5;
                        Location particleLoc = new Location(loc.getWorld(), x, loc.getY() + 1, z);
                        player.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 1, 0.5, 0.5, 0.5, 0.02);
                    }
                }, 0L, 5L);
                buffZoneParticleTasks.put(uuid, particleTask);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    BukkitTask t = buffZoneParticleTasks.remove(uuid);
                    if (t != null) t.cancel();
                }, 40L);
            }
        }
    }

    private boolean isInElementBuffZone(PlayerMoveEvent event) {
        if (!pkBridge.isEnabled()) return false;
        String villageElement = villageManager.getVillageElementByPlayer(event.getPlayer().getUniqueId());
        if (villageElement == null) return false;
        Village village = villageManager.getVillageByName(villageManager.getVillageNameByPlayer(event.getPlayer().getUniqueId()));
        if (village == null || !villageElement.equalsIgnoreCase(village.getElement())) return false;
        FileConfiguration config = plugin.getConfig();
        double minDistance = config.getDouble("projectkorra.element_buff_zone.min_distance_chunks", 8.0);
        double perMember = config.getDouble("projectkorra.element_buff_zone.distance_per_member", 0.5);
        int members = village.getMembers().size();
        double maxDistance = minDistance + (perMember * members);
        String playerWorld = event.getTo().getWorld().getName();
        int playerChunkX = event.getTo().getChunk().getX();
        int playerChunkZ = event.getTo().getChunk().getZ();
        for (Claim claim : village.getClaims()) {
            if (!claim.getChunk().getWorld().equals(playerWorld)) continue;
            int dist = Math.max(Math.abs(claim.getChunk().getX() - playerChunkX), Math.abs(claim.getChunk().getZ() - playerChunkZ));
            if (dist <= maxDistance) {
                return true;
            }
        }
        return false;
    }

    private double getBuffPercent(Player player) {
        String villageName = villageManager.getVillageNameByPlayer(player.getUniqueId());
        Village village = villageManager.getVillageByName(villageName);
        if (village == null) return 0.0;
        double bonusPerMember = plugin.getConfig().getDouble("projectkorra.element_damage_bonus_per_member", 0.03);
        int members = village.getMembers().size();
        return bonusPerMember * members * 100.0;
    }

    private String parseColor(String msg) {
        return msg == null ? "" : org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);
    }
} 