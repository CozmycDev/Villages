package net.doodcraft.cozmyc.villages.models;

import org.bukkit.Location;

import java.util.*;

public class Village {
    private String name;
    private UUID mayor;
    private Set<UUID> members;
    private Set<UUID> coOwners;
    private Set<Claim> claims;
    private Map<ChunkCoord, UUID> privateChunkOwners;
    private long lastMayorLogin;
    private boolean allowMembersToEditUnassignedChunks;
    private boolean serverVillage;
    private Location spawnLocation;
    private String element;
    private double villageCreationCost;
    private Map<Integer, Location> outpostSpawns = new HashMap<>();
    private int nextOutpostIndex = 1;
    private ChunkCoord mainClaimChunk;

    public Village(String name, UUID mayor) {
        this(name, mayor, 0);
    }
    public Village(String name, UUID mayor, double villageCreationCost) {
        this.name = name;
        this.mayor = mayor;
        this.members = new HashSet<>();
        this.coOwners = new HashSet<>();
        this.claims = new HashSet<>();
        this.privateChunkOwners = new HashMap<>();
        this.allowMembersToEditUnassignedChunks = false;
        this.lastMayorLogin = System.currentTimeMillis();
        this.serverVillage = false;
        this.spawnLocation = null;
        this.element = null;
        this.villageCreationCost = villageCreationCost;
        // Add mayor to members if not null
        if (mayor != null) {
            this.members.add(mayor);
        }
    }

    public Set<Claim> getClaims() {
        return claims;
    }

    public void addClaim(Claim claim) {
        claims.add(claim);
    }

    public String getName() {
        return name;
    }

    public UUID getMayor() {
        return mayor;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Set<UUID> getCoOwners() {
        return coOwners;
    }

    public void setCoOwners(Set<UUID> coOwners) {
        this.coOwners = coOwners;
    }

    public void setMembers(Set<UUID> members) {
        this.members = members;
    }

    public void setLastMayorLogin(long lastMayorLogin) {
        this.lastMayorLogin = lastMayorLogin;
    }

    public void setName(String name) { this.name = name; }
    public void setMayor(UUID mayor) { this.mayor = mayor; }
    public void setClaims(Set<Claim> claims) { this.claims = claims; }
    public void setPrivateChunkOwners(Map<ChunkCoord, UUID> privateChunkOwners) { this.privateChunkOwners = privateChunkOwners; }
    public Map<ChunkCoord, UUID> getPrivateChunkOwners() { return privateChunkOwners; }
    public long getLastMayorLogin() { return lastMayorLogin; }

    public boolean hasClaim(ChunkCoord chunk) {
        for (Claim claim : claims) {
            if (claim.getChunk().equals(chunk)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAllowMembersToEditUnassignedChunks() {
        return allowMembersToEditUnassignedChunks;
    }

    public void setAllowMembersToEditUnassignedChunks(boolean allowMembersToEditUnassignedChunks) {
        this.allowMembersToEditUnassignedChunks = allowMembersToEditUnassignedChunks;
    }

    public boolean isServerVillage() {
        return serverVillage;
    }

    public void setServerVillage(boolean serverVillage) {
        this.serverVillage = serverVillage;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation;
    }

    public boolean hasSpawnSet() {
        return spawnLocation != null;
    }

    public String getElement() {
        return element;
    }

    public void setElement(String element) {
        this.element = element;
    }

    public double getVillageCreationCost() { return villageCreationCost; }
    public void setVillageCreationCost(double cost) { this.villageCreationCost = cost; }

    public Map<Integer, Location> getOutpostSpawns() { return outpostSpawns; }
    public int getNextOutpostIndex() { return nextOutpostIndex; }
    public void setNextOutpostIndex(int idx) { this.nextOutpostIndex = idx; }

    public void setOutpostSpawn(int index, Location loc) { outpostSpawns.put(index, loc); }
    public Location getOutpostSpawn(int index) { return outpostSpawns.get(index); }
    public boolean hasOutpostSpawn(int index) { return outpostSpawns.containsKey(index); }

    /**
     * Get all claims that are part of the main area.
     */
    public Set<Claim> getMainAreaClaims() {
        Set<Claim> main = new HashSet<>();
        for (Claim c : claims) {
            if (c.isMainArea()) main.add(c);
        }
        return main;
    }

    /**
     * Get all claims for a specific outpost index.
     */
    public Set<Claim> getOutpostClaims(int outpostIndex) {
        Set<Claim> outpost = new HashSet<>();
        for (Claim c : claims) {
            if (!c.isMainArea() && c.getOutpostIndex() != null && c.getOutpostIndex() == outpostIndex) outpost.add(c);
        }
        return outpost;
    }

    /**
     * Get all outpost indices present in this village.
     */
    public Set<Integer> getOutpostIndices() {
        Set<Integer> indices = new HashSet<>();
        for (Claim c : claims) {
            if (!c.isMainArea() && c.getOutpostIndex() != null) indices.add(c.getOutpostIndex());
        }
        return indices;
    }

    public ChunkCoord getMainClaimChunk() { return mainClaimChunk; }
    public void setMainClaimChunk(ChunkCoord chunk) { this.mainClaimChunk = chunk; }
} 