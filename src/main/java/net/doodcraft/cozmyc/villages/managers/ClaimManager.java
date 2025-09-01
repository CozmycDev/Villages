package net.doodcraft.cozmyc.villages.managers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.doodcraft.cozmyc.villages.models.ChunkCoord;
import net.doodcraft.cozmyc.villages.models.Claim;
import net.doodcraft.cozmyc.villages.models.Village;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.*;

public class ClaimManager {
    private final Map<ChunkCoord, Claim> chunkToClaim = new HashMap<>();
    private final Map<Village, Set<Claim>> villageToClaims = new HashMap<>();

    public void addClaim(Village village, Claim claim) {
        chunkToClaim.put(claim.getChunk(), claim);
        villageToClaims.computeIfAbsent(village, v -> new HashSet<>()).add(claim);
    }

    public void removeClaim(Village village, Claim claim) {
        chunkToClaim.remove(claim.getChunk());
        Set<Claim> claims = villageToClaims.get(village);
        if (claims != null) {
            claims.remove(claim);
            if (claims.isEmpty()) villageToClaims.remove(village);
        }
    }

    public Claim getClaim(ChunkCoord chunk) {
        return chunkToClaim.get(chunk);
    }

    public boolean isChunkClaimed(ChunkCoord chunk) {
        return chunkToClaim.containsKey(chunk);
    }

    public Set<Claim> getClaimsForVillage(Village village) {
        return villageToClaims.getOrDefault(village, Collections.emptySet());
    }

    // Check if a chunk is within buffer distance of another village
    public boolean isChunkNearOtherVillage(ChunkCoord chunk, Village self, int buffer) {
        for (Village village : villageToClaims.keySet()) {
            if (village.equals(self)) continue;
            for (Claim claim : getClaimsForVillage(village)) {
                ChunkCoord other = claim.getChunk();
                if (chunk.getWorld().equals(other.getWorld())) {
                    int dx = Math.abs(chunk.getX() - other.getX());
                    int dz = Math.abs(chunk.getZ() - other.getZ());
                    if (dx <= buffer && dz <= buffer) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Check if a chunk overlaps an existing WorldGuard region
    public boolean isChunkOverlappingWorldGuardRegion(ChunkCoord chunk) {
        World world = Bukkit.getWorld(chunk.getWorld());
        if (world == null) return false;
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
        if (regionManager == null) return false;
        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        for (ProtectedRegion region : regionManager.getRegions().values()) {
            if (region.getMaximumPoint().getBlockX() < minX || region.getMinimumPoint().getBlockX() > maxX) continue;
            if (region.getMaximumPoint().getBlockZ() < minZ || region.getMinimumPoint().getBlockZ() > maxZ) continue;
            // Overlaps
            return true;
        }
        return false;
    }

    // Check if a chunk is within buffer distance of a WorldGuard region
    public boolean isChunkNearWorldGuardRegion(ChunkCoord chunk, int buffer) {
        World world = Bukkit.getWorld(chunk.getWorld());
        if (world == null) return false;
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
        if (regionManager == null) return false;
        int minX = (chunk.getX() - buffer) << 4;
        int minZ = (chunk.getZ() - buffer) << 4;
        int maxX = ((chunk.getX() + buffer) << 4) + 15;
        int maxZ = ((chunk.getZ() + buffer) << 4) + 15;
        for (ProtectedRegion region : regionManager.getRegions().values()) {
            // Skip regions that are village claims
            if (region.getId().startsWith("village_")) continue;
            // If the region is completely outside the buffer area, skip
            if (region.getMaximumPoint().getBlockX() < minX || region.getMinimumPoint().getBlockX() > maxX) continue;
            if (region.getMaximumPoint().getBlockZ() < minZ || region.getMinimumPoint().getBlockZ() > maxZ) continue;
            // Region is within buffer area
            return true;
        }
        return false;
    }
} 