package net.doodcraft.cozmyc.villages.maps;

import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.models.Claim;
import net.doodcraft.cozmyc.villages.models.Village;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class XaeroMapIntegration implements Listener {

    // todo: work in progress, might finish this at a later date.

    private static final String WORLDMAP_CHANNEL = "xaeroworldmap:main";
    private static final String MINIMAP_CHANNEL = "xaerominimap:main";
    private static final int CLAIMS_PER_PACKET = 1;
    private static final int PACKET_DELAY = 20;
    
    private final VillagesPlugin plugin;
    
    public XaeroMapIntegration(VillagesPlugin plugin) {
        this.plugin = plugin;

        //plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, WORLDMAP_CHANNEL);
        //plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, MINIMAP_CHANNEL);
    }
    
    public void onDisable() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin);
    }
    
//    @EventHandler
//    public void onPlayerRegisterChannel(PlayerRegisterChannelEvent event) {
//        String channel = event.getChannel();
//        if (!channel.equals(WORLDMAP_CHANNEL) && !channel.equals(MINIMAP_CHANNEL)) {
//            return;
//        }
//
//        Bukkit.getScheduler().runTaskLater(VillagesPlugin.getInstance(), () -> {
//            sendWorldId(event.getPlayer(), channel);
//            sendClaimsData(event.getPlayer(), channel);
//        }, 40L);
//    }
//
//    @EventHandler
//    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
//        Bukkit.getScheduler().runTaskLater(VillagesPlugin.getInstance(), () -> {
//            Player player = event.getPlayer();
//            sendWorldId(player, WORLDMAP_CHANNEL);
//            sendWorldId(player, MINIMAP_CHANNEL);
//            sendClaimsData(player, WORLDMAP_CHANNEL);
//            sendClaimsData(player, MINIMAP_CHANNEL);
//        }, 40L);
//    }
    
    private void sendWorldId(Player player, String channel) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(stream);
        
        try {
            out.writeByte(0);
            out.writeInt(getWorldId(player.getWorld().getName()));
            player.sendPluginMessage(plugin, channel, stream.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send world ID to " + player.getName());
        }
    }

    private void sendClaimsData(Player player, String channel) {
        List<Claim> worldClaims = new ArrayList<>();
        for (Village village : plugin.getVillageManager().getAllVillages()) {
            for (Claim claim : village.getClaims()) {
                if (claim.getChunk().getWorld().equals(player.getWorld().getName())) {
                    worldClaims.add(claim);
                }
            }
        }

        System.out.println("[Villages] Found " + worldClaims.size() + " claims to send to " + player.getName() + " on channel " + channel);

        final int[] delay = {0};
        for (Claim claim : worldClaims) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(stream);

                try {
                    if (player == null || !player.isOnline()) {
                        return;
                    }

                    String villageName = plugin.getVillageManager().getVillageNameByChunk(claim.getChunk());
                    if (villageName == null) return;
                    Village village = plugin.getVillageManager().getVillageByName(villageName);
                    if (village == null) return;

                    // Ensure name is short enough
                    if (villageName.length() > 32) {
                        villageName = villageName.substring(0, 32);
                    }

                    int x = claim.getChunk().getX() * 16;
                    int z = claim.getChunk().getZ() * 16;
                    int width = 16;
                    int height = 16;
                    int color = getVillageColor(village);

                    out.writeByte(1);
                    out.writeInt(x);
                    out.writeInt(z);
                    out.writeInt(width);
                    out.writeInt(height);
                    out.writeUTF(villageName); // Includes 2-byte length prefix
                    out.writeInt(color);
                    out.writeBoolean(true);
                    out.writeFloat(0.5f);

                    byte[] data = stream.toByteArray();
                    System.out.println("[Villages] Sending claim packet to " + player.getName()
                            + " | Chunk: (" + x + ", " + z + ") | Village: " + villageName
                            + " | Bytes: " + data.length);

                    // Optional: Check max expected size
                    if (data.length > 256) {
                        System.out.println("[Villages] Packet too large! Skipping...");
                        return;
                    }

                    player.sendPluginMessage(plugin, channel, data);

                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to send claim data to " + player.getName() + ": " + e.getMessage());
                }

            }, delay[0]);
            delay[0] += PACKET_DELAY;
        }
    }
    
    private int getWorldId(String worldName) {
        return worldName.hashCode();
    }
    
    private int getVillageColor(Village village) {
        String name = village.getName();
        int hash = name.hashCode();

        return 0xFF000000 | (hash & 0x00FFFFFF);
    }
} 