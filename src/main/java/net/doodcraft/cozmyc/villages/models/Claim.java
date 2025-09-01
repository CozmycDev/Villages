package net.doodcraft.cozmyc.villages.models;

import java.util.UUID;

public class Claim {
    private String villageName;
    private ChunkCoord chunk;
    private UUID owner; // null if village-owned
    private java.util.Set<UUID> allowedMembers = new java.util.HashSet<>();
    private double costPaid;
    private boolean mainArea; // true if part of main area, false if outpost
    private Integer outpostIndex; // null if main area, otherwise outpost index

    /**
     * Standard constructor for main area claim
     */
    public Claim(String villageName, ChunkCoord chunk, UUID owner, double costPaid) {
        this(villageName, chunk, owner, costPaid, true, null);
    }

    /**
     * Full constructor for specifying main/outpost and index
     */
    public Claim(String villageName, ChunkCoord chunk, UUID owner, double costPaid, boolean mainArea, Integer outpostIndex) {
        this.villageName = villageName;
        this.chunk = chunk;
        this.owner = owner;
        this.allowedMembers = new java.util.HashSet<>();
        this.costPaid = costPaid;
        this.mainArea = mainArea;
        this.outpostIndex = outpostIndex;
    }

    public ChunkCoord getChunk() {
        return chunk;
    }

    public String getVillageName() {
        return villageName;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public java.util.Set<UUID> getAllowedMembers() {
        return allowedMembers;
    }

    public void allowMember(UUID uuid) {
        allowedMembers.add(uuid);
    }

    public void disallowMember(UUID uuid) {
        allowedMembers.remove(uuid);
    }

    public boolean isMemberAllowed(UUID uuid) {
        return allowedMembers.contains(uuid);
    }

    public void setVillageName(String villageName) { this.villageName = villageName; }
    public void setChunk(ChunkCoord chunk) { this.chunk = chunk; }
    public UUID getOwner() { return owner; }
    public void setAllowedMembers(java.util.Set<UUID> allowedMembers) { this.allowedMembers = allowedMembers; }
    public double getCostPaid() { return costPaid; }
    public void setCostPaid(double costPaid) { this.costPaid = costPaid; }
    public boolean isMainArea() { return mainArea; }
    public void setMainArea(boolean mainArea) { this.mainArea = mainArea; }
    public Integer getOutpostIndex() { return outpostIndex; }
    public void setOutpostIndex(Integer outpostIndex) { this.outpostIndex = outpostIndex; }
} 