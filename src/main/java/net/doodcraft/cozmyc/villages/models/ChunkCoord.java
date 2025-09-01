package net.doodcraft.cozmyc.villages.models;

import org.bukkit.Chunk;

public class ChunkCoord {
    private String world;
    private int x, z;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkCoord that = (ChunkCoord) o;
        return x == that.x && z == that.z && world.equals(that.world);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(world, x, z);
    }

    public int distanceTo(ChunkCoord other) {
        if (!world.equals(other.world)) return Integer.MAX_VALUE;
        int dx = x - other.x;
        int dz = z - other.z;
        return Math.max(Math.abs(dx), Math.abs(dz));
    }

    public boolean isAdjacentTo(ChunkCoord other) {
        if (!world.equals(other.world)) return false;
        int dx = Math.abs(x - other.x);
        int dz = Math.abs(z - other.z);
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }

    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getZ() { return z; }
    public void setWorld(String world) { this.world = world; }
    public void setX(int x) { this.x = x; }
    public void setZ(int z) { this.z = z; }

    public ChunkCoord(String world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    public ChunkCoord(Chunk chunk) {
        this(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }
} 