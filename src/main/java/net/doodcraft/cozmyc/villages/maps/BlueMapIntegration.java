package net.doodcraft.cozmyc.villages.maps;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.models.Claim;
import net.doodcraft.cozmyc.villages.models.Village;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.*;

public class BlueMapIntegration implements Listener {

    private final VillagesPlugin plugin;
    private final Map<String, MarkerSet> markerSets = new HashMap<>();
    private static final String MARKER_SET_ID = "villages";
    private static final String MARKER_SET_LABEL = "Village Claims";
    
    public BlueMapIntegration(VillagesPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        if (Bukkit.getPluginManager().getPlugin("BlueMap") != null) {
            initialize();
        }
    }
    
    @EventHandler
    public void onBlueMapEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("BlueMap")) {
            initialize();
        }
    }
    
    private void initialize() {
        BlueMapAPI.onEnable(api -> {
            // Create marker set
            MarkerSet markerSet = MarkerSet.builder()
                .label(MARKER_SET_LABEL)
                .defaultHidden(false)
                .build();
            
            // Add marker set to all maps
            for (BlueMapMap map : api.getMaps()) {
                map.getMarkerSets().put(MARKER_SET_ID, markerSet);
                markerSets.put(map.getId(), markerSet);
            }
            
            // Update all claims
            updateAllClaims();
        });
    }
    
    public void updateAllClaims() {
        BlueMapAPI.getInstance().ifPresent(api -> {
            for (Village village : plugin.getVillageManager().getAllVillages()) {
                updateVillageClaims(village);
            }
        });
    }

    private float getGroupMaxY(World world, Set<Claim> group) {
        int maxY = 0;
        for (Claim claim : group) {
            int startX = claim.getChunk().getX() * 16;
            int startZ = claim.getChunk().getZ() * 16;
            for (int x = startX; x < startX + 16; x++) {
                for (int z = startZ; z < startZ + 16; z++) {
                    int y = world.getHighestBlockYAt(x, z);
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        plugin.getLogger().info("Calculating Y for group of " + group.size() + " claims");
        plugin.getLogger().info("Highest Y in group: " + maxY);

        if (maxY <= 0f) return 64f;

        return (float) maxY;
    }

    public void updateVillageClaims(Village village) {
        BlueMapAPI.getInstance().ifPresent(api -> {
            // Group claims by world
            Map<String, List<Claim>> claimsByWorld = new HashMap<>();
            for (Claim claim : village.getClaims()) {
                String worldName = claim.getChunk().getWorld();
                claimsByWorld.computeIfAbsent(worldName, k -> new ArrayList<>()).add(claim);
            }

            for (Map.Entry<String, List<Claim>> entry : claimsByWorld.entrySet()) {
                String worldName = entry.getKey();
                List<Claim> claimsInWorld = entry.getValue();

                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                BlueMapMap map = api.getMap(world.getName()).orElse(null);
                if (map == null) continue;

                MarkerSet markerSet = markerSets.get(map.getId());
                if (markerSet == null) continue;

                // group connected claims (connected via N/S/E/W)
                List<Set<Claim>> connectedGroups = groupConnectedClaims(claimsInWorld);

                int index = 0;
                for (Set<Claim> group : connectedGroups) {
                    Color blueMapColor = getVillageBlueMapColorWithElement(village);

                    float groupY = getGroupMaxY(world, group);

                    for (Claim claim : group) {
                        int x = claim.getChunk().getX() * 16;
                        int z = claim.getChunk().getZ() * 16;

                        Shape shape = Shape.createRect(x, z, x + 16, z + 16);

                        String markerId = "village_" + village.getName() + "_" + worldName + "_" + index++;

                        ShapeMarker marker = ShapeMarker.builder()
                                .label(village.getName().replaceAll("_", " "))
                                .shape(shape, groupY)
                                .fillColor(blueMapColor)
                                .lineColor(blueMapColor)
                                .lineWidth(1)
                                .build();

                        markerSet.getMarkers().put(markerId, marker);
                    }
                }
            }
        });
    }

    private Color getVillageBlueMapColor(String villageName) {
        int hash = villageName.hashCode();
        float hue = (hash & 0xFF) / 255f * 0.7f; // 0.0-0.7
        float saturation = 0.8f + ((hash >> 8) & 0xFF) / 255f * 0.2f; // 0.8-1.0
        float lightness = 0.5f + ((hash >> 16) & 0xFF) / 255f * 0.1f; // 0.5-0.6

        float r, g, b;
        if (saturation == 0f) {
            r = g = b = lightness;
        } else {
            float q = lightness < 0.5f ? lightness * (1f + saturation) : lightness + saturation - lightness * saturation;
            float p = 2f * lightness - q;
            r = hueToRgb(p, q, hue + 1f / 3f);
            g = hueToRgb(p, q, hue);
            b = hueToRgb(p, q, hue - 1f / 3f);
        }

        return new Color(
                (int)(r * 255),
                (int)(g * 255),
                (int)(b * 255),
                0.4f
        );
    }

    private Color getVillageBlueMapColorWithElement(Village village) {
//        if (plugin.getPKBridge().isEnabled() && village.getElement() != null) {
//            // todo: use PK colors
//        }
        return getVillageBlueMapColor(village.getName());
    }

    private float hueToRgb(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f/6f) return p + (q - p) * 6f * t;
        if (t < 1f/2f) return q;
        if (t < 2f/3f) return p + (q - p) * (2f/3f - t) * 6f;
        return p;
    }

    private List<Set<Claim>> groupConnectedClaims(List<Claim> claims) {
        List<Set<Claim>> groups = new ArrayList<>();
        Set<Claim> unvisited = new HashSet<>(claims);

        while (!unvisited.isEmpty()) {
            Set<Claim> group = new HashSet<>();
            Queue<Claim> queue = new LinkedList<>();
            Claim start = unvisited.iterator().next();

            queue.add(start);
            group.add(start);
            unvisited.remove(start);

            while (!queue.isEmpty()) {
                Claim current = queue.poll();

                for (Iterator<Claim> it = unvisited.iterator(); it.hasNext(); ) {
                    Claim next = it.next();
                    if (areChunksTouching(current, next)) {
                        queue.add(next);
                        group.add(next);
                        it.remove();
                    }
                }
            }

            groups.add(group);
        }

        return groups;
    }

    private boolean areChunksTouching(Claim a, Claim b) {
        if (!a.getChunk().getWorld().equals(b.getChunk().getWorld())) return false;

        int dx = Math.abs(a.getChunk().getX() - b.getChunk().getX());
        int dz = Math.abs(a.getChunk().getZ() - b.getChunk().getZ());

        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }
    
    public void removeClaim(Claim claim, Village village) {
        BlueMapAPI.getInstance().ifPresent(api -> {
            World world = Bukkit.getWorld(claim.getChunk().getWorld());
            if (world == null) return;
            
            BlueMapMap map = api.getMap(world.getName()).orElse(null);
            if (map == null) return;
            
            MarkerSet markerSet = markerSets.get(map.getId());
            if (markerSet == null) return;
            
            String markerId = "village_" + village.getName() + "_" + claim.getChunk().getX() + "_" + claim.getChunk().getZ();
            markerSet.getMarkers().remove(markerId);
        });
    }
}
