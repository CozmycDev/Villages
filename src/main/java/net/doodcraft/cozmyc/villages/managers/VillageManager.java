package net.doodcraft.cozmyc.villages.managers;

import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.models.ChunkCoord;
import net.doodcraft.cozmyc.villages.models.Claim;
import net.doodcraft.cozmyc.villages.models.Village;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class VillageManager {
    private final VillagesPlugin plugin;
    private final Map<String, Village> villages;
    private final Map<UUID, String> playerVillage; // player UUID -> village name
    private final File villagesFile;
    private FileConfiguration villagesConfig;
    private Village serverVillage;

    public VillageManager(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.villages = new HashMap<>();
        this.playerVillage = new HashMap<>();
        this.villagesFile = new File(plugin.getDataFolder(), "villages.yml");
        this.villagesConfig = YamlConfiguration.loadConfiguration(villagesFile);
    }

    public boolean isPlayerInVillage(UUID player) {
        return playerVillage.containsKey(player);
    }

    public boolean isVillageNameTaken(String name) {
        return villages.containsKey(name.toLowerCase());
    }

    public Village createVillage(String name, UUID mayor) {
        Village village = new Village(name, mayor);
        villages.put(name.toLowerCase(), village);
        playerVillage.put(mayor, name.toLowerCase());
        return village;
    }

    public String getVillageNameByPlayer(UUID player) {
        return playerVillage.get(player);
    }

    public Village getVillageByName(String name) {
        Village village = villages.get(name.toLowerCase());
        if (village != null) {
            return village;
        }

        String normalizedName = name.toLowerCase().replaceAll("_", " ");
        for (Map.Entry<String, Village> entry : villages.entrySet()) {
            if (entry.getKey().replaceAll("_", " ").equals(normalizedName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public boolean isChunkClaimed(ChunkCoord chunk) {
        for (Village v : villages.values()) {
            for (Claim c : v.getClaims()) {
                if (c.getChunk().equals(chunk)) return true;
            }
        }
        return false;
    }

    public boolean isChunkNearOtherVillage(ChunkCoord chunk, String myVillage, int minDistance) {
        for (Village v : villages.values()) {
            if (v.getName().equalsIgnoreCase(myVillage)) continue;
            for (Claim c : v.getClaims()) {
                if (chunk.distanceTo(c.getChunk()) < minDistance) return true;
            }
        }
        return false;
    }

    public boolean isChunkAdjacentToVillage(ChunkCoord chunk, Village village) {
        for (Claim c : village.getClaims()) {
            if (chunk.isAdjacentTo(c.getChunk())) return true;
        }
        return village.getClaims().isEmpty();
    }

    private int getNextOutpostIndex(Village village) {
        int max = 0;
        for (Claim c : village.getClaims()) {
            if (!c.isMainArea() && c.getOutpostIndex() != null && c.getOutpostIndex() > max) {
                max = c.getOutpostIndex();
            }
        }
        return max + 1;
    }

    /**
     * Claim a chunk for a village, enforcing main/outpost separation.
     * - If adjacent to main area, add to main area.
     * - If adjacent to an outpost, add to that outpost.
     * - If not adjacent to any, create a new outpost.
     * - If would connect main and outpost, or two outposts, deny.
     */
    public boolean claimChunk(Village village, ChunkCoord chunk, UUID owner, double costPaid) {
        Set<Claim> claims = village.getClaims();
        if (claims.isEmpty()) {
            Claim claim = new Claim(village.getName(), chunk, owner, costPaid, true, null);
            village.addClaim(claim);
            plugin.getClaimManager().addClaim(village, claim);
            village.setMainClaimChunk(chunk);
            if (plugin.getBlueMapIntegration() != null) {
                plugin.getBlueMapIntegration().updateVillageClaims(village);
            }
            return true;
        }
        // Check adjacency
        boolean adjacentToMain = false;
        Integer adjacentOutpost = null;
        for (Claim c : claims) {
            if (chunk.isAdjacentTo(c.getChunk())) {
                if (c.isMainArea()) adjacentToMain = true;
                else if (c.getOutpostIndex() != null) {
                    if (adjacentOutpost == null) adjacentOutpost = c.getOutpostIndex();
                    else if (!adjacentOutpost.equals(c.getOutpostIndex())) {
                        // Would connect two outposts, deny
                        return false;
                    }
                }
            }
        }
        if (adjacentToMain && adjacentOutpost != null) {
            // Would connect main and outpost, deny
            return false;
        }
        if (adjacentToMain) {
            // Add to main area
            Claim claim = new Claim(village.getName(), chunk, owner, costPaid, true, null);
            village.addClaim(claim);
            plugin.getClaimManager().addClaim(village, claim);
            if (village.getMainClaimChunk() == null) {
                village.setMainClaimChunk(chunk);
            }
            if (plugin.getBlueMapIntegration() != null) {
                plugin.getBlueMapIntegration().updateVillageClaims(village);
            }
            return true;
        } else if (adjacentOutpost != null) {
            // Add to existing outpost
            Claim claim = new Claim(village.getName(), chunk, owner, costPaid, false, adjacentOutpost);
            village.addClaim(claim);
            plugin.getClaimManager().addClaim(village, claim);
            if (plugin.getBlueMapIntegration() != null) {
                plugin.getBlueMapIntegration().updateVillageClaims(village);
            }
            return true;
        } else {
            // Not adjacent to anything, create new outpost
            int newOutpostIdx = getNextOutpostIndex(village);
            Claim claim = new Claim(village.getName(), chunk, owner, costPaid, false, newOutpostIdx);
            village.addClaim(claim);
            plugin.getClaimManager().addClaim(village, claim);
            if (plugin.getBlueMapIntegration() != null) {
                plugin.getBlueMapIntegration().updateVillageClaims(village);
            }
            return true;
        }
    }

    public void claimChunk(Village village, ChunkCoord chunk, UUID owner) {
        claimChunk(village, chunk, owner, 0);
    }

    public void saveAll() {
        saveData();
    }

    public void loadAll() {
        loadData();
    }

    public void saveData() {
        try {
            for (String key : villagesConfig.getKeys(false)) {
                villagesConfig.set(key, null);
            }

            // Save each village
            for (Map.Entry<String, Village> entry : villages.entrySet()) {
                String villageName = entry.getKey();
                Village village = entry.getValue();
                
                String path = "villages." + villageName;
                villagesConfig.set(path + ".name", village.getName());
                villagesConfig.set(path + ".mayor", village.getMayor() != null ? village.getMayor().toString() : null);
                villagesConfig.set(path + ".lastMayorLogin", village.getLastMayorLogin());
                villagesConfig.set(path + ".allowMembersToEditUnassignedChunks", village.isAllowMembersToEditUnassignedChunks());
                villagesConfig.set(path + ".serverVillage", village.isServerVillage());
                
                // Save spawn location if set
                if (village.hasSpawnSet()) {
                    Location spawn = village.getSpawnLocation();
                    villagesConfig.set(path + ".spawn.world", spawn.getWorld().getName());
                    villagesConfig.set(path + ".spawn.x", spawn.getX());
                    villagesConfig.set(path + ".spawn.y", spawn.getY());
                    villagesConfig.set(path + ".spawn.z", spawn.getZ());
                    villagesConfig.set(path + ".spawn.yaw", spawn.getYaw());
                    villagesConfig.set(path + ".spawn.pitch", spawn.getPitch());
                }
                
                // Save members
                ArrayList<String> members = new ArrayList<>();
                if (village.getMembers() != null) {
                    for (UUID member : village.getMembers()) {
                        if (member != null) {
                            try {
                                members.add(member.toString());
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to save member UUID for village " + villageName + ": " + e.getMessage());
                            }
                        }
                    }
                }
                villagesConfig.set(path + ".members", members);
                
                // Save co-owners
                ArrayList<String> coOwners = new ArrayList<>();
                if (village.getCoOwners() != null) {
                    for (UUID coOwner : village.getCoOwners()) {
                        if (coOwner != null) {
                            try {
                                coOwners.add(coOwner.toString());
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to save co-owner UUID for village " + villageName + ": " + e.getMessage());
                            }
                        }
                    }
                }
                villagesConfig.set(path + ".coOwners", coOwners);
                
                // Save claims
                ArrayList<Map<String, Object>> claimsList = new ArrayList<>();
                if (village.getClaims() != null) {
                    for (Claim claim : village.getClaims()) {
                        if (claim != null) {
                            try {
                                Map<String, Object> claimMap = new HashMap<>();
                                claimMap.put("world", claim.getChunk().getWorld());
                                claimMap.put("x", claim.getChunk().getX());
                                claimMap.put("z", claim.getChunk().getZ());
                                claimMap.put("owner", claim.getOwner() != null ? claim.getOwner().toString() : null);
                                claimMap.put("costPaid", claim.getCostPaid());
                                claimMap.put("mainArea", claim.isMainArea());
                                claimMap.put("outpostIndex", claim.getOutpostIndex());
                                claimsList.add(claimMap);
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to save claim for village " + villageName + ": " + e.getMessage());
                            }
                        }
                    }
                }
                villagesConfig.set(path + ".claims", claimsList);
                
                // Save private chunk owners
                Map<String, String> privateOwners = new HashMap<>();
                if (village.getPrivateChunkOwners() != null) {
                    for (Map.Entry<ChunkCoord, UUID> ownerEntry : village.getPrivateChunkOwners().entrySet()) {
                        if (ownerEntry.getKey() != null && ownerEntry.getValue() != null) {
                            try {
                                String chunkKey = ownerEntry.getKey().getWorld() + "," + 
                                                ownerEntry.getKey().getX() + "," + 
                                                ownerEntry.getKey().getZ();
                                privateOwners.put(chunkKey, ownerEntry.getValue().toString());
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to save private chunk owner for village " + villageName + ": " + e.getMessage());
                            }
                        }
                    }
                }
                villagesConfig.set(path + ".privateChunkOwners", privateOwners);

                // Save element
                villagesConfig.set(path + ".element", village.getElement());

                // Save outpost spawns
                Map<String, Map<String, Object>> outpostSpawns = new HashMap<>();
                for (Map.Entry<Integer, Location> outpostEntry : village.getOutpostSpawns().entrySet()) {
                    Location loc = outpostEntry.getValue();
                    Map<String, Object> locMap = new HashMap<>();
                    locMap.put("world", loc.getWorld().getName());
                    locMap.put("x", loc.getX());
                    locMap.put("y", loc.getY());
                    locMap.put("z", loc.getZ());
                    locMap.put("yaw", loc.getYaw());
                    locMap.put("pitch", loc.getPitch());
                    outpostSpawns.put(String.valueOf(outpostEntry.getKey()), locMap);
                }
                villagesConfig.set(path + ".outpostSpawns", outpostSpawns);
                villagesConfig.set(path + ".villageCreationCost", village.getVillageCreationCost());
            }
            
            // Save to file
            villagesConfig.save(villagesFile);
            //plugin.getLogger().info("Successfully saved village data!");
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save village data to " + villagesFile, e);
        }
    }

    public void loadData() {
        try {
            if (!villagesFile.exists()) {
                plugin.getLogger().info("No villages.yml found, creating new file.");
                villagesFile.getParentFile().mkdirs();
                villagesFile.createNewFile();
                return;
            }

            // Clear existing data
            villages.clear();
            playerVillage.clear();
            serverVillage = null;
            
            // Load villages
            if (villagesConfig.contains("villages")) {
                for (String villageName : villagesConfig.getConfigurationSection("villages").getKeys(false)) {
                    String path = "villages." + villageName;
                    
                    // Basic village info
                    String name = villagesConfig.getString(path + ".name");
                    if (name == null) {
                        plugin.getLogger().warning("Village name is null for path: " + path);
                        continue;
                    }
                    
                    String mayorStr = villagesConfig.getString(path + ".mayor");
                    UUID mayor = mayorStr != null ? UUID.fromString(mayorStr) : null;
                    long lastMayorLogin = villagesConfig.getLong(path + ".lastMayorLogin", System.currentTimeMillis());
                    boolean allowMembersToEditUnassignedChunks = villagesConfig.getBoolean(path + ".allowMembersToEditUnassignedChunks", false);
                    boolean isServerVillage = villagesConfig.getBoolean(path + ".serverVillage", false);
                    
                    Village village = new Village(name, mayor);
                    village.setLastMayorLogin(lastMayorLogin);
                    village.setAllowMembersToEditUnassignedChunks(allowMembersToEditUnassignedChunks);
                    village.setServerVillage(isServerVillage);
                    
                    // Load spawn location if exists
                    if (villagesConfig.contains(path + ".spawn")) {
                        String worldName = villagesConfig.getString(path + ".spawn.world");
                        double x = villagesConfig.getDouble(path + ".spawn.x");
                        double y = villagesConfig.getDouble(path + ".spawn.y");
                        double z = villagesConfig.getDouble(path + ".spawn.z");
                        float yaw = (float) villagesConfig.getDouble(path + ".spawn.yaw");
                        float pitch = (float) villagesConfig.getDouble(path + ".spawn.pitch");
                        
                        World world = plugin.getServer().getWorld(worldName);
                        if (world != null) {
                            Location spawn = new Location(world, x, y, z, yaw, pitch);
                            village.setSpawnLocation(spawn);
                        }
                    }
                    
                    // If this is the server village, store it
                    if (isServerVillage) {
                        serverVillage = village;
                    }
                    
                    // Load members
                    java.util.List<String> membersList = villagesConfig.getStringList(path + ".members");
                    Set<UUID> members = new HashSet<>();
                    for (String memberStr : membersList) {
                        try {
                            UUID memberUuid = UUID.fromString(memberStr);
                            members.add(memberUuid);
                            playerVillage.put(memberUuid, name.toLowerCase());
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid member UUID in village " + name + ": " + memberStr);
                        }
                    }
                    village.setMembers(members);
                    
                    // Load co-owners
                    java.util.List<String> coOwnersList = villagesConfig.getStringList(path + ".coOwners");
                    Set<UUID> coOwners = new HashSet<>();
                    for (String coOwnerStr : coOwnersList) {
                        try {
                            UUID coOwnerUuid = UUID.fromString(coOwnerStr);
                            coOwners.add(coOwnerUuid);
                            playerVillage.put(coOwnerUuid, name.toLowerCase());
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid co-owner UUID in village " + name + ": " + coOwnerStr);
                        }
                    }
                    village.setCoOwners(coOwners);
                    
                    // Add mayor to playerVillage map if not null
                    if (mayor != null) {
                        playerVillage.put(mayor, name.toLowerCase());
                    }
                    
                    // Load claims
                    java.util.List<Map<?, ?>> claimsList = villagesConfig.getMapList(path + ".claims");
                    Set<Claim> claims = new HashSet<>();
                    for (Map<?, ?> claimMap : claimsList) {
                        try {
                            String world = (String) claimMap.get("world");
                            int x = (int) claimMap.get("x");
                            int z = (int) claimMap.get("z");
                            String ownerStr = (String) claimMap.get("owner");
                            double costPaid = 0;
                            if (claimMap.containsKey("costPaid")) {
                                Object costObj = claimMap.get("costPaid");
                                if (costObj instanceof Number) {
                                    costPaid = ((Number) costObj).doubleValue();
                                } else if (costObj != null) {
                                    try { costPaid = Double.parseDouble(costObj.toString()); } catch (Exception ignore) {}
                                }
                            } else {
                                costPaid = plugin.getConfig().getDouble("village.claim_cost", 32);
                            }
                            boolean isMainArea = true;
                            if (claimMap.containsKey("mainArea")) {
                                Object mainAreaObj = claimMap.get("mainArea");
                                if (mainAreaObj instanceof Boolean) isMainArea = (Boolean) mainAreaObj;
                                else if (mainAreaObj != null) isMainArea = Boolean.parseBoolean(mainAreaObj.toString());
                            }
                            Integer outpostIndex = null;
                            if (claimMap.containsKey("outpostIndex")) {
                                Object outpostObj = claimMap.get("outpostIndex");
                                if (outpostObj instanceof Integer) outpostIndex = (Integer) outpostObj;
                                else if (outpostObj != null) {
                                    try { outpostIndex = Integer.parseInt(outpostObj.toString()); } catch (Exception ignore) {}
                                }
                            }
                            ChunkCoord chunk = new ChunkCoord(world, x, z);
                            UUID owner = ownerStr != null ? UUID.fromString(ownerStr) : null;
                            Claim claim = new Claim(name, chunk, owner, costPaid, isMainArea, outpostIndex);
                            claims.add(claim);
                            plugin.getClaimManager().addClaim(village, claim);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error loading claim for village " + name + ": " + e.getMessage());
                        }
                    }
                    village.setClaims(claims);
                    // Load outpost spawns
                    if (villagesConfig.contains(path + ".outpostSpawns")) {
                        org.bukkit.configuration.ConfigurationSection spawnsSection = villagesConfig.getConfigurationSection(path + ".outpostSpawns");
                        if (spawnsSection != null) {
                            for (String key : spawnsSection.getKeys(false)) {
                                try {
                                    int idx = Integer.parseInt(key);
                                    String worldName = spawnsSection.getString(key + ".world");
                                    double x = spawnsSection.getDouble(key + ".x");
                                    double y = spawnsSection.getDouble(key + ".y");
                                    double z = spawnsSection.getDouble(key + ".z");
                                    float yaw = (float) spawnsSection.getDouble(key + ".yaw");
                                    float pitch = (float) spawnsSection.getDouble(key + ".pitch");
                                    World world = plugin.getServer().getWorld(worldName);
                                    if (world != null) {
                                        Location loc = new Location(world, x, y, z, yaw, pitch);
                                        village.setOutpostSpawn(idx, loc);
                                    }
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Failed to load outpost spawn for village " + name + " index " + key + ": " + e.getMessage());
                                }
                            }
                        }
                    }
                    
                    // After loading all claims in loadData, collapse outpost indices
                    // Remove mainClaim loading and saving
                    // Collapse outpost indices
                    Map<Integer, Integer> oldToNewIndex = new HashMap<>();
                    int nextIdx = 1;
                    Set<Integer> usedIndices = new TreeSet<>();
                    for (Claim c : claims) {
                        if (!c.isMainArea() && c.getOutpostIndex() != null) usedIndices.add(c.getOutpostIndex());
                    }
                    for (Integer oldIdx : usedIndices) {
                        oldToNewIndex.put(oldIdx, nextIdx++);
                    }
                    // Update claims
                    for (Claim c : claims) {
                        if (!c.isMainArea() && c.getOutpostIndex() != null) {
                            c.setOutpostIndex(oldToNewIndex.get(c.getOutpostIndex()));
                        }
                    }
                    // Update outpost spawns
                    Map<Integer, Location> newOutpostSpawns = new HashMap<>();
                    for (Map.Entry<Integer, Location> entry : village.getOutpostSpawns().entrySet()) {
                        Integer oldIdx = entry.getKey();
                        if (oldToNewIndex.containsKey(oldIdx)) {
                            newOutpostSpawns.put(oldToNewIndex.get(oldIdx), entry.getValue());
                        }
                    }
                    village.getOutpostSpawns().clear();
                    village.getOutpostSpawns().putAll(newOutpostSpawns);
                    // Save updated indices to disk to keep config in sync
                    saveData();
                    
                    // Load private chunk owners
                    if (villagesConfig.contains(path + ".privateChunkOwners")) {
                        Map<ChunkCoord, UUID> privateOwners = new HashMap<>();
                        org.bukkit.configuration.ConfigurationSection privateOwnersSection = villagesConfig.getConfigurationSection(path + ".privateChunkOwners");
                        if (privateOwnersSection != null) {
                            for (String key : privateOwnersSection.getKeys(false)) {
                                try {
                                    String[] parts = key.split(",");
                                    if (parts.length != 3) {
                                        plugin.getLogger().warning("Invalid private chunk owner key format in village " + name + ": " + key);
                                        continue;
                                    }
                                    ChunkCoord chunk = new ChunkCoord(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                                    String ownerStr = privateOwnersSection.getString(key);
                                    if (ownerStr != null) {
                                        privateOwners.put(chunk, UUID.fromString(ownerStr));
                                    }
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Error loading private chunk owner for village " + name + ": " + e.getMessage());
                                }
                            }
                        }
                        village.setPrivateChunkOwners(privateOwners);
                    }

                    // Load element
                    if (villagesConfig.contains(path + ".element")) {
                        village.setElement(villagesConfig.getString(path + ".element"));
                    }
                    if (villagesConfig.contains(path + ".villageCreationCost")) {
                        village.setVillageCreationCost(villagesConfig.getDouble(path + ".villageCreationCost"));
                    }

                    // Store village with lowercase name for consistency
                    villages.put(name.toLowerCase(), village);
                    plugin.getLogger().info("Loaded village: " + name + (isServerVillage ? " (Server Village)" : ""));
                }
            }
            
            plugin.getLogger().info("Successfully loaded " + villages.size() + " villages!");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load village data from " + villagesFile, e);
        }
    }

    public void addPlayerVillageMap(UUID member, String villageName) {
        playerVillage.put(member, villageName);
    }

    public void removePlayerVillageMap(UUID member) {
        playerVillage.remove(member);
    }

    // Delete village, unclaim all claims, and return totalMoneySpent for refund
    public double deleteVillageAndRefund(String name) {
        Village v = villages.remove(name.toLowerCase());
        double totalSpent = 0;
        if (v != null) {
            // Always refund creation cost
            totalSpent += v.getVillageCreationCost();
            // Sum all claim costs before unclaiming
            Set<Claim> claims = new HashSet<>(v.getClaims());
            // Delete all regions before unclaiming
            for (Claim claim : claims) {
                World world = plugin.getServer().getWorld(claim.getChunk().getWorld());
                if (world != null) {
                    String regionId = "village_" + v.getName().toLowerCase() + "_" + claim.getChunk().getX() + "_" + claim.getChunk().getZ();
                    net.doodcraft.cozmyc.villages.utils.WGUtils.deleteRegion(world, regionId);
                }
            }
            for (Claim claim : claims) {
                totalSpent += claim.getCostPaid();
            }
            // Unclaim all claims
            for (Claim claim : claims) {
                unclaimChunk(v, claim.getChunk());
            }
            for (UUID member : v.getMembers()) {
                playerVillage.remove(member);
            }
            v.setVillageCreationCost(0);
        }
        return totalSpent;
    }

    public Set<String> getAllVillageNames() {
        //return new HashSet<>(villages.keySet());
        HashSet<String> names = new HashSet<>();
        for (Village v : getAllVillages()) {
            names.add(v.getName().replaceAll("_", " "));
        }
        return names;
    }

    public java.util.Collection<Village> getAllVillages() {
        return villages.values();
    }

    public void renameVillage(String oldName, String newName) {
        Village village = villages.get(oldName.toLowerCase());
        if (village != null) {
            String originalNewName = newName;
            newName = newName.toLowerCase();
            
            // Remove old entry
            villages.remove(oldName.toLowerCase());
            // Update village name
            village.setName(originalNewName);
            // Add new entry
            villages.put(newName, village);
            // Update player mappings
            for (Map.Entry<UUID, String> entry : playerVillage.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(oldName)) {
                    playerVillage.put(entry.getKey(), newName);
                }
            }
            // Update claim village names
            for (Claim claim : village.getClaims()) {
                claim.setVillageName(originalNewName);
            }
        }
    }

    public String getVillageNameByChunk(ChunkCoord chunk) {
        for (Map.Entry<String, Village> entry : villages.entrySet()) {
            if (entry.getValue().hasClaim(chunk)) {
                return entry.getValue().getName(); // Return the actual village name with proper case
            }
        }
        return null;
    }

    public Village getServerVillage() {
        return serverVillage;
    }

    public Village createServerVillage() {
        if (serverVillage != null) {
            return serverVillage;
        }
        serverVillage = new Village("Spawn", null);
        serverVillage.setServerVillage(true);
        villages.put(serverVillage.getName(), serverVillage);
        return serverVillage;
    }

    public boolean isServerVillage(String villageName) {
        return serverVillage != null && serverVillage.getName().equals(villageName);
    }

    public boolean unclaimChunk(Village village, ChunkCoord chunk) {
        if (village == null || chunk == null) return false;
        Claim claimToRemove = null;
        for (Claim claim : village.getClaims()) {
            if (claim.getChunk().equals(chunk)) {
                claimToRemove = claim;
                break;
            }
        }
        if (claimToRemove == null) return false;
        // Prevent unclaiming last main area claim if outposts exist
        if (claimToRemove.isMainArea()) {
            int mainClaims = 0;
            for (Claim c : village.getClaims()) if (c.isMainArea()) mainClaims++;
            if (mainClaims == 1) {
                // Check for any outpost claims
                for (Claim c : village.getClaims()) {
                    if (!c.isMainArea()) return false;
                }
            }
        }
        village.getClaims().remove(claimToRemove);
        village.getPrivateChunkOwners().remove(chunk);
        plugin.getClaimManager().removeClaim(village, claimToRemove);
        World world = plugin.getServer().getWorld(claimToRemove.getChunk().getWorld());
        if (world != null) {
            String regionId = "village_" + village.getName().toLowerCase() + "_" + claimToRemove.getChunk().getX() + "_" + claimToRemove.getChunk().getZ();
            net.doodcraft.cozmyc.villages.utils.WGUtils.deleteRegion(world, regionId);
        }
        if (plugin.getBlueMapIntegration() != null) {
            plugin.getBlueMapIntegration().removeClaim(claimToRemove, village);
        }
        if (!claimToRemove.isMainArea()) {
            cleanupOutpostIndices(village);
        }
        // Remove main spawn if this chunk is the main spawn
        if (village.getSpawnLocation() != null && claimToRemove.getChunk().getWorld().equals(village.getSpawnLocation().getWorld().getName())
                && claimToRemove.getChunk().getX() == village.getSpawnLocation().getChunk().getX()
                && claimToRemove.getChunk().getZ() == village.getSpawnLocation().getChunk().getZ()) {
            village.setSpawnLocation(null);
        }
        // Remove outpost spawn if this chunk is an outpost spawn chunk
        for (Map.Entry<Integer, Location> entry : new HashMap<>(village.getOutpostSpawns()).entrySet()) {
            Location outpostLoc = entry.getValue();
            if (outpostLoc != null && outpostLoc.getWorld().getName().equals(claimToRemove.getChunk().getWorld())
                    && outpostLoc.getChunk().getX() == claimToRemove.getChunk().getX()
                    && outpostLoc.getChunk().getZ() == claimToRemove.getChunk().getZ()) {
                village.getOutpostSpawns().remove(entry.getKey());
            }
        }
        return true;
    }

    // Flood fill to find all connected claims from a starting chunk
    @Deprecated
    private Set<Claim> getConnectedClaims(Village village, ChunkCoord start) {
        Set<Claim> allClaims = new HashSet<>(village.getClaims());
        Map<ChunkCoord, Claim> chunkToClaim = new HashMap<>();
        for (Claim claim : allClaims) {
            chunkToClaim.put(claim.getChunk(), claim);
        }
        Set<Claim> connected = new HashSet<>();
        Set<ChunkCoord> visited = new HashSet<>();
        Queue<ChunkCoord> queue = new java.util.LinkedList<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            ChunkCoord current = queue.poll();
            Claim claim = chunkToClaim.get(current);
            if (claim != null) {
                connected.add(claim);
                // Check all 4 adjacent chunks
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) != 1) continue; // Only cardinal directions
                        ChunkCoord neighbor = new ChunkCoord(current.getWorld(), current.getX() + dx, current.getZ() + dz);
                        if (!visited.contains(neighbor) && chunkToClaim.containsKey(neighbor)) {
                            queue.add(neighbor);
                            visited.add(neighbor);
                        }
                    }
                }
            }
        }
        return connected;
    }

    @Deprecated
    public Set<Claim> getConnectedClaims(Village village, ChunkCoord start, Set<Claim> claims) {
        Map<ChunkCoord, Claim> chunkToClaim = new HashMap<>();
        for (Claim claim : claims) {
            chunkToClaim.put(claim.getChunk(), claim);
        }
        Set<Claim> connected = new HashSet<>();
        Set<ChunkCoord> visited = new HashSet<>();
        Queue<ChunkCoord> queue = new java.util.LinkedList<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            ChunkCoord current = queue.poll();
            Claim claim = chunkToClaim.get(current);
            if (claim != null) {
                connected.add(claim);
                // Check all 4 adjacent chunks
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) != 1) continue; // Only cardinal directions
                        ChunkCoord neighbor = new ChunkCoord(current.getWorld(), current.getX() + dx, current.getZ() + dz);
                        if (!visited.contains(neighbor) && chunkToClaim.containsKey(neighbor)) {
                            queue.add(neighbor);
                            visited.add(neighbor);
                        }
                    }
                }
            }
        }
        return connected;
    }

    public void setVillageElement(String villageName, String element) {
        Village village = getVillageByName(villageName);
        if (village != null) {
            village.setElement(element);
        }
    }

    public String getVillageElement(String villageName) {
        Village village = getVillageByName(villageName);
        return village != null ? village.getElement() : null;
    }

    public String getVillageElementByPlayer(UUID player) {
        String villageName = getVillageNameByPlayer(player);
        return villageName != null ? getVillageElement(villageName) : null;
    }

    private void cleanupOutpostIndices(Village village) {
        Set<Claim> claims = village.getClaims();
        Set<Integer> usedIndices = new TreeSet<>();
        for (Claim c : claims) {
            if (!c.isMainArea() && c.getOutpostIndex() != null) usedIndices.add(c.getOutpostIndex());
        }
        Set<Integer> spawnsToRemove = new HashSet<>();
        for (Integer idx : new HashSet<>(village.getOutpostSpawns().keySet())) {
            if (!usedIndices.contains(idx)) spawnsToRemove.add(idx);
        }
        for (Integer idx : spawnsToRemove) {
            village.getOutpostSpawns().remove(idx);
        }
        // Collapse outpost indices for claims and spawns
        Map<Integer, Integer> oldToNewIndex = new HashMap<>();
        int nextIdx = 1;
        for (Integer oldIdx : usedIndices) {
            oldToNewIndex.put(oldIdx, nextIdx++);
        }
        // Update claims
        for (Claim c : claims) {
            if (!c.isMainArea() && c.getOutpostIndex() != null) {
                c.setOutpostIndex(oldToNewIndex.get(c.getOutpostIndex()));
            }
        }
        // Update outpost spawns
        Map<Integer, Location> newOutpostSpawns = new HashMap<>();
        for (Map.Entry<Integer, Location> entry : village.getOutpostSpawns().entrySet()) {
            Integer oldIdx = entry.getKey();
            if (oldToNewIndex.containsKey(oldIdx)) {
                newOutpostSpawns.put(oldToNewIndex.get(oldIdx), entry.getValue());
            }
        }
        village.getOutpostSpawns().clear();
        village.getOutpostSpawns().putAll(newOutpostSpawns);
    }
} 