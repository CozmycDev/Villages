package net.doodcraft.cozmyc.villages.utils;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.models.Claim;
import net.doodcraft.cozmyc.villages.models.Village;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class WGUtils {
    private static String logPrefix = null;
    private static String getLogPrefix() {
        if (logPrefix == null) {
            String prefix = VillagesPlugin.getInstance().getConfig().getString("messages.prefix", "[Villages]");
            logPrefix = prefix.replaceAll("§[0-9A-FK-ORa-fk-or]", "").replaceAll("&[0-9A-FK-ORa-fk-or]", "").trim() + " ";
        }
        return logPrefix;
    }

    /**
     * Create or replace a chunk region with WorldGuard flags and owner/member.
     * @param world Bukkit world
     * @param regionId Unique region ID
     * @param cx Chunk X
     * @param cz Chunk Z
     * @param owner Player owner (null for village-owned)
     */
    public static void createChunkRegion(World world, String regionId, int cx, int cz, Player owner) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager manager = container.get(BukkitAdapter.adapt(world));
        if (manager == null) {
            Bukkit.getLogger().log(Level.WARNING, getLogPrefix() + "WorldGuard RegionManager is null for world " + world.getName());
            return;
        }
        // Remove old region if exists
        if (manager.hasRegion(regionId)) {
            manager.removeRegion(regionId);
        }
        int bx = cx << 4;
        int bz = cz << 4;
        BlockVector3 min = BlockVector3.at(bx, 0, bz);
        BlockVector3 max = BlockVector3.at(bx + 15, world.getMaxHeight(), bz + 15);
        ProtectedRegion region = new ProtectedCuboidRegion(regionId, min, max);

        // Extract village name from region ID
        String[] parts = regionId.split("_");
        if (parts.length >= 2) {
            // Reconstruct the village name from parts, handling underscores
            StringBuilder villageNameBuilder = new StringBuilder();
            for (int i = 1; i < parts.length - 2; i++) {
                if (i > 1) villageNameBuilder.append("_");
                villageNameBuilder.append(parts[i]);
            }
            String villageName = villageNameBuilder.toString();
            Village village = VillagesPlugin.getInstance().getVillageManager().getVillageByName(villageName);

            if (village != null) {
                // Add mayor and co-owners as owners
                region.getOwners().addPlayer(village.getMayor());
                for (UUID coOwner : village.getCoOwners()) {
                    region.getOwners().addPlayer(coOwner);
                }

                // For privately owned chunks
                if (owner != null) {
                    region.getOwners().addPlayer(owner.getUniqueId());
                    Bukkit.getLogger().log(Level.INFO, getLogPrefix() + "Created private region " + regionId + " for owner " + owner.getName());
                } else {
                    // For village-owned chunks, add all village members as members
                    for (UUID member : village.getMembers()) {
                        // Skip mayor and co-owners as they're already owners
                        if (!member.equals(village.getMayor()) && !village.getCoOwners().contains(member)) {
                            region.getMembers().addPlayer(member);
                        }
                    }
                    Bukkit.getLogger().log(Level.INFO, getLogPrefix() + "Created village region " + regionId + " for village " + villageName);
                }

                // Find the claim for this chunk to set proper flags
                Claim claim = null;
                for (Claim c : village.getClaims()) {
                    if (c.getChunk().getX() == cx && c.getChunk().getZ() == cz) {
                        claim = c;
                        break;
                    }
                }
                updateRegionFlags(region, village, claim);
            }
        }

        // Add region
        manager.addRegion(region);
    }

    /**
     * Update region members (for allowed players in a chunk).
     * @param world Bukkit world
     * @param regionId Region ID
     * @param members Set of UUIDs to allow
     */
    @Deprecated
    public static void updateChunkRegionMembers(World world, String regionId, Set<UUID> members) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager manager = container.get(BukkitAdapter.adapt(world));
        if (manager == null) {
            Bukkit.getLogger().log(Level.WARNING, getLogPrefix() + "WorldGuard RegionManager is null for world " + world.getName());
            return;
        }
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) {
            Bukkit.getLogger().log(Level.WARNING, getLogPrefix() + "Region " + regionId + " not found in world " + world.getName());
            return;
        }

        // Extract village name from region ID to get mayor and co-owners
        String[] parts = regionId.split("_");
        if (parts.length >= 2) {
            String villageName = parts[1];
            Village village = VillagesPlugin.getInstance().getVillageManager().getVillageByName(villageName);

            if (village != null) {
                // Make sure mayor and co-owners are owners before updating members
                ensureMayorAndCoOwnersAreOwners(region, village);
            }
        }

        region.getMembers().clear();
        for (UUID uuid : members) {
            region.getMembers().addPlayer(uuid);
        }
    }

    /**
     * Update both owners and members for a region (for private/village-owned chunks).
     * @param world Bukkit world
     * @param regionId Region ID
     * @param owners Set of UUIDs (empty for village-owned)
     * @param members Set of UUIDs
     */
    public static void updateChunkRegionOwnersAndMembers(Village village, World world, String regionId, Set<UUID> owners, Set<UUID> members) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager manager = container.get(BukkitAdapter.adapt(world));
        if (manager == null) {
            Bukkit.getLogger().log(Level.WARNING, getLogPrefix() + "WorldGuard RegionManager is null for world " + world.getName());
            return;
        }
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) {
            Bukkit.getLogger().log(Level.WARNING, getLogPrefix() + "Region " + regionId + " not found in world " + world.getName());
            return;
        }

        if (village != null) {
            Set<UUID> allOwners = new HashSet<>(owners);

            // Always include mayor and co-owners as region owners
            allOwners.add(village.getMayor());
            allOwners.addAll(village.getCoOwners());

            // Clear and set all owners
            region.getOwners().clear();
            for (UUID uuid : allOwners) {
                region.getOwners().addPlayer(uuid);
            }

            // Clear and set all members
            region.getMembers().clear();
            for (UUID uuid : members) {
                if (!allOwners.contains(uuid)) {
                    region.getMembers().addPlayer(uuid);
                }
            }

            String[] parts = regionId.split("_");
            if (parts.length >= 4) {
                int cx = Integer.parseInt(parts[parts.length - 2]);
                int cz = Integer.parseInt(parts[parts.length - 1]);
                Claim claim = null;
                for (Claim c : village.getClaims()) {
                    if (c.getChunk().getX() == cx && c.getChunk().getZ() == cz) {
                        claim = c;
                        break;
                    }
                }
                updateRegionFlags(region, village, claim);
            }
        } else {
            // Fallback if village is not found
            region.getOwners().clear();
            for (UUID uuid : owners) {
                region.getOwners().addPlayer(uuid);
            }

            region.getMembers().clear();
            for (UUID uuid : members) {
                region.getMembers().addPlayer(uuid);
            }
        }

        ensureMayorAndCoOwnersAreOwners(region, village);
    }

    /**
     * Helper method to ensure mayor and co-owners are always region owners
     * @param region The region to update
     * @param village The village object
     */
    private static void ensureMayorAndCoOwnersAreOwners(ProtectedRegion region, Village village) {
        // Add mayor as owner if not already
        if (!region.getOwners().contains(village.getMayor())) {
            region.getOwners().addPlayer(village.getMayor());
        }

        // Add all co-owners as owners if not already
        for (UUID coOwner : village.getCoOwners()) {
            if (!region.getOwners().contains(coOwner)) {
                region.getOwners().addPlayer(coOwner);
            }
        }
    }

    /**
     * Delete a WorldGuard region by ID.
     */
    public static void deleteRegion(World world, String regionId) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager manager = container.get(BukkitAdapter.adapt(world));
        if (manager == null) {
            Bukkit.getLogger().log(Level.WARNING, getLogPrefix() + "WorldGuard RegionManager is null for world " + world.getName());
            return;
        }
        manager.removeRegion(regionId);
    }

    /**
     * Update region flags based on village settings and claim ownership.
     * Only sets flags that are currently null to preserve mayor/co-owner customizations.
     * @param region The region to update
     * @param village The village object
     * @param claim The claim object (can be null)
     */
    private static void updateRegionFlags(ProtectedRegion region, Village village, Claim claim) {
        // Set minimum viable defaults only if not already set
        if (region.getFlag(Flags.ENDERDRAGON_BLOCK_DAMAGE) == null) {
            region.setFlag(Flags.ENDERDRAGON_BLOCK_DAMAGE, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.ENDER_BUILD) == null) {
            region.setFlag(Flags.ENDER_BUILD, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.CREEPER_EXPLOSION) == null) {
            region.setFlag(Flags.CREEPER_EXPLOSION, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.BREEZE_WIND_CHARGE) == null) {
            region.setFlag(Flags.BREEZE_WIND_CHARGE, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.GHAST_FIREBALL) == null) {
            region.setFlag(Flags.GHAST_FIREBALL, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.OTHER_EXPLOSION) == null) {
            region.setFlag(Flags.OTHER_EXPLOSION, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.LIGHTER.getRegionGroupFlag()) == null) {
            region.setFlag(Flags.LIGHTER.getRegionGroupFlag(), RegionGroup.MEMBERS);
        }
        if (region.getFlag(Flags.LIGHTER) == null) {
            region.setFlag(Flags.LIGHTER, StateFlag.State.ALLOW);
        }
        if (region.getFlag(Flags.TRAMPLE_BLOCKS) == null) {
            region.setFlag(Flags.TRAMPLE_BLOCKS, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.DESTROY_VEHICLE) == null) {
            region.setFlag(Flags.DESTROY_VEHICLE, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.RAVAGER_RAVAGE) == null) {
            region.setFlag(Flags.RAVAGER_RAVAGE, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.ENTITY_ITEM_FRAME_DESTROY) == null) {
            region.setFlag(Flags.ENTITY_ITEM_FRAME_DESTROY, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.ENTITY_PAINTING_DESTROY) == null) {
            region.setFlag(Flags.ENTITY_PAINTING_DESTROY, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.LAVA_FIRE) == null) {
            region.setFlag(Flags.LAVA_FIRE, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.MYCELIUM_SPREAD) == null) {
            region.setFlag(Flags.MYCELIUM_SPREAD, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.LIGHTNING) == null) {
            region.setFlag(Flags.LIGHTNING, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.WITHER_DAMAGE) == null) {
            region.setFlag(Flags.WITHER_DAMAGE, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.FIRE_SPREAD) == null) {
            region.setFlag(Flags.FIRE_SPREAD, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.WATER_FLOW) == null) {
            region.setFlag(Flags.WATER_FLOW, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.LAVA_FLOW) == null) {
            region.setFlag(Flags.LAVA_FLOW, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.PVP) == null) {
            region.setFlag(Flags.PVP, StateFlag.State.ALLOW);
        }

        if (region.getFlag(Flags.DENY_MESSAGE) == null) {
            region.setFlag(Flags.DENY_MESSAGE, "§7§oThis land is protected by a magical force.");
        }
        if (region.getFlag(Flags.ENTRY_DENY_MESSAGE) == null) {
            region.setFlag(Flags.ENTRY_DENY_MESSAGE, "");
        }
        if (region.getFlag(Flags.EXIT_DENY_MESSAGE) == null) {
            region.setFlag(Flags.EXIT_DENY_MESSAGE, "");
        }

        if (claim != null && claim.getOwner() != null) {
            // Private chunk, only set if not already set
            if (region.getFlag(Flags.BLOCK_PLACE.getRegionGroupFlag()) == null) {
                region.setFlag(Flags.BLOCK_PLACE.getRegionGroupFlag(), RegionGroup.MEMBERS);
            }
            if (region.getFlag(Flags.BLOCK_PLACE) == null) {
                region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.ALLOW);
            }

            if (region.getFlag(Flags.BLOCK_BREAK.getRegionGroupFlag()) == null) {
                region.setFlag(Flags.BLOCK_BREAK.getRegionGroupFlag(), RegionGroup.MEMBERS);
            }
            if (region.getFlag(Flags.BLOCK_BREAK) == null) {
                region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
            }

            if (region.getFlag(Flags.USE.getRegionGroupFlag()) == null) {
                region.setFlag(Flags.USE.getRegionGroupFlag(), RegionGroup.MEMBERS);
            }
            if (region.getFlag(Flags.USE) == null) {
                region.setFlag(Flags.USE, StateFlag.State.ALLOW);
            }

            if (region.getFlag(Flags.INTERACT.getRegionGroupFlag()) == null) {
                region.setFlag(Flags.INTERACT.getRegionGroupFlag(), RegionGroup.MEMBERS);
            }
            if (region.getFlag(Flags.INTERACT) == null) {
                region.setFlag(Flags.INTERACT, StateFlag.State.ALLOW);
            }

            if (region.getFlag(Flags.PISTONS) == null) {
                region.setFlag(Flags.PISTONS, StateFlag.State.ALLOW);
            }
            if (region.getFlag(Flags.SCULK_GROWTH) == null) {
                region.setFlag(Flags.SCULK_GROWTH, StateFlag.State.ALLOW);
            }

            if (region.getFlag(Flags.ITEM_DROP) == null) {
                region.setFlag(Flags.ITEM_DROP, StateFlag.State.ALLOW);
            }
            if (region.getFlag(Flags.ITEM_PICKUP) == null) {
                region.setFlag(Flags.ITEM_PICKUP, StateFlag.State.ALLOW);
            }

        } else {
            if (village.isAllowMembersToEditUnassignedChunks()) {
                if (region.getFlag(Flags.BLOCK_PLACE.getRegionGroupFlag()) == null) {
                    region.setFlag(Flags.BLOCK_PLACE.getRegionGroupFlag(), RegionGroup.MEMBERS);
                }
                if (region.getFlag(Flags.BLOCK_PLACE) == null) {
                    region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.ALLOW);
                }

                if (region.getFlag(Flags.BLOCK_BREAK.getRegionGroupFlag()) == null) {
                    region.setFlag(Flags.BLOCK_BREAK.getRegionGroupFlag(), RegionGroup.MEMBERS);
                }
                if (region.getFlag(Flags.BLOCK_BREAK) == null) {
                    region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
                }

                if (region.getFlag(Flags.USE.getRegionGroupFlag()) == null) {
                    region.setFlag(Flags.USE.getRegionGroupFlag(), RegionGroup.MEMBERS);
                }
                if (region.getFlag(Flags.USE) == null) {
                    region.setFlag(Flags.USE, StateFlag.State.ALLOW);
                }

                if (region.getFlag(Flags.INTERACT.getRegionGroupFlag()) == null) {
                    region.setFlag(Flags.INTERACT.getRegionGroupFlag(), RegionGroup.MEMBERS);
                }
                if (region.getFlag(Flags.INTERACT) == null) {
                    region.setFlag(Flags.INTERACT, StateFlag.State.ALLOW);
                }

                if (region.getFlag(Flags.PISTONS) == null) {
                    region.setFlag(Flags.PISTONS, StateFlag.State.ALLOW);
                }
                if (region.getFlag(Flags.SCULK_GROWTH) == null) {
                    region.setFlag(Flags.SCULK_GROWTH, StateFlag.State.ALLOW);
                }

            } else {
                if (region.getFlag(Flags.BLOCK_PLACE.getRegionGroupFlag()) == null) {
                    region.setFlag(Flags.BLOCK_PLACE.getRegionGroupFlag(), RegionGroup.OWNERS);
                }
                if (region.getFlag(Flags.BLOCK_PLACE) == null) {
                    region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.ALLOW);
                }

                if (region.getFlag(Flags.BLOCK_BREAK.getRegionGroupFlag()) == null) {
                    region.setFlag(Flags.BLOCK_BREAK.getRegionGroupFlag(), RegionGroup.OWNERS);
                }
                if (region.getFlag(Flags.BLOCK_BREAK) == null) {
                    region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
                }

                if (region.getFlag(Flags.USE.getRegionGroupFlag()) == null) {
                    region.setFlag(Flags.USE.getRegionGroupFlag(), RegionGroup.OWNERS);
                }
                if (region.getFlag(Flags.USE) == null) {
                    region.setFlag(Flags.USE, StateFlag.State.ALLOW);
                }

                if (region.getFlag(Flags.INTERACT.getRegionGroupFlag()) == null) {
                    region.setFlag(Flags.INTERACT.getRegionGroupFlag(), RegionGroup.OWNERS);
                }
                if (region.getFlag(Flags.INTERACT) == null) {
                    region.setFlag(Flags.INTERACT, StateFlag.State.ALLOW);
                }

                // Allow pistons and sculk growth for all village members (cross-chunk compatibility)
                if (region.getFlag(Flags.PISTONS) == null) {
                    region.setFlag(Flags.PISTONS, StateFlag.State.ALLOW);
                }
                if (region.getFlag(Flags.SCULK_GROWTH) == null) {
                    region.setFlag(Flags.SCULK_GROWTH, StateFlag.State.ALLOW);
                }
            }

            // Always allow item transfer for hoppers within village chunks
            if (region.getFlag(Flags.ITEM_DROP) == null) {
                region.setFlag(Flags.ITEM_DROP, StateFlag.State.ALLOW);
            }
            if (region.getFlag(Flags.ITEM_PICKUP) == null) {
                region.setFlag(Flags.ITEM_PICKUP, StateFlag.State.ALLOW);
            }
        }

        if (region.getFlag(Flags.BUILD) == null) {
            region.setFlag(Flags.BUILD, StateFlag.State.ALLOW);
        }

        applyConfigFlagDefaults(region);
    }

    /**
     * Applies global config-based flag defaults to a region. Only sets flags that are currently null.
     */
    private static void applyConfigFlagDefaults(ProtectedRegion region) {
        org.bukkit.configuration.ConfigurationSection flagSection = VillagesPlugin.getInstance().getConfig().getConfigurationSection("worldguard.flags");
        if (flagSection == null) return;
        var registry = WorldGuard.getInstance().getFlagRegistry();
        for (String key : flagSection.getKeys(false)) {
            String value = flagSection.getString(key);
            try {
                // Handle group flags (e.g. block-place-group: MEMBERS)
                if (key.endsWith("-group")) {
                    String flagName = key.substring(0, key.length() - 6); // remove -group
                    com.sk89q.worldguard.protection.flags.Flag<?> flag = registry.get(flagName);
                    if (flag == null || flag.getRegionGroupFlag() == null) {
                        Bukkit.getLogger().warning(getLogPrefix() + "Unknown or non-group WorldGuard flag in config: " + key);
                        continue;
                    }
                    // Only set if not already set
                    if (region.getFlag(flag.getRegionGroupFlag()) == null) {
                        try {
                            RegionGroup group = RegionGroup.valueOf(value.toUpperCase());
                            region.setFlag(flag.getRegionGroupFlag(), group);
                        } catch (IllegalArgumentException e) {
                            Bukkit.getLogger().warning(getLogPrefix() + "Invalid group value for flag '" + key + "': " + value);
                        }
                    }
                    continue;
                }
                // Always skip build flag (set in code only)
                if (key.equalsIgnoreCase("build")) continue;
                com.sk89q.worldguard.protection.flags.Flag<?> flag = registry.get(key);
                if (flag == null) {
                    Bukkit.getLogger().warning(getLogPrefix() + "Unknown WorldGuard flag in config: " + key);
                    continue;
                }
                // Only set if not already set
                if (region.getFlag(flag) != null) continue;
                // StateFlag (ALLOW/DENY)
                if (flag instanceof StateFlag) {
                    StateFlag.State state = null;
                    if (value.equalsIgnoreCase("allow")) state = StateFlag.State.ALLOW;
                    else if (value.equalsIgnoreCase("deny")) state = StateFlag.State.DENY;
                    else if (value.equalsIgnoreCase("none") || value.equalsIgnoreCase("null")) state = null;
                    else {
                        Bukkit.getLogger().warning(getLogPrefix() + "Invalid value for state flag '" + key + "': " + value);
                        continue;
                    }
                    region.setFlag((StateFlag) flag, state);
                } else if (flag instanceof com.sk89q.worldguard.protection.flags.EnumFlag) {
                    com.sk89q.worldguard.protection.flags.EnumFlag<? extends Enum<?>> enumFlag = (com.sk89q.worldguard.protection.flags.EnumFlag<? extends Enum<?>>) flag;
                    Enum<?> parsed = enumFlag.unmarshal(value);
                    @SuppressWarnings("unchecked")
                    com.sk89q.worldguard.protection.flags.EnumFlag rawEnumFlag = enumFlag;
                    region.setFlag(rawEnumFlag, parsed);
                } else if (flag instanceof com.sk89q.worldguard.protection.flags.StringFlag) {
                    region.setFlag((com.sk89q.worldguard.protection.flags.StringFlag) flag, value);
                } else if (flag instanceof com.sk89q.worldguard.protection.flags.IntegerFlag) {
                    try {
                        Integer intValue = Integer.valueOf(value);
                        region.setFlag((com.sk89q.worldguard.protection.flags.IntegerFlag) flag, intValue);
                    } catch (NumberFormatException e) {
                        Bukkit.getLogger().warning(getLogPrefix() + "Invalid integer for flag '" + key + "': " + value);
                    }
                } else if (flag instanceof com.sk89q.worldguard.protection.flags.DoubleFlag) {
                    try {
                        Double doubleValue = Double.valueOf(value);
                        region.setFlag((com.sk89q.worldguard.protection.flags.DoubleFlag) flag, doubleValue);
                    } catch (NumberFormatException e) {
                        Bukkit.getLogger().warning(getLogPrefix() + "Invalid double for flag '" + key + "': " + value);
                    }
                } else {
                    Bukkit.getLogger().warning(getLogPrefix() + "Unsupported flag type for '" + key + "'. Skipping.");
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning(getLogPrefix() + "Failed to apply flag '" + key + "' from config: " + e.getMessage());
            }
        }
    }

    /**
     * Update region flags for all village claims.
     * @param village The village object
     * @param world The world to update regions in
     */
    public static void updateVillageRegionFlags(Village village, World world) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager manager = container.get(BukkitAdapter.adapt(world));
        if (manager == null) {
            Bukkit.getLogger().log(Level.WARNING, getLogPrefix() + "WorldGuard RegionManager is null for world " + world.getName());
            return;
        }

        for (Claim claim : village.getClaims()) {
            String regionId = "village_" + village.getName().toLowerCase() + "_" + claim.getChunk().getX() + "_" + claim.getChunk().getZ();
            ProtectedRegion region = manager.getRegion(regionId);
            if (region != null) {
                updateRegionFlags(region, village, claim);
            }
        }
    }

    public static void createServerChunkRegion(World world, String regionId, int cx, int cz) {
        RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (regions == null) return;

        // Create the region
        ProtectedRegion region = new ProtectedCuboidRegion(
                regionId,
                BlockVector3.at(cx * 16, 0, cz * 16),
                BlockVector3.at(cx * 16 + 15, world.getMaxHeight(), cz * 16 + 15)
        );

        // Set flags for server village only if not already set
        if (region.getFlag(Flags.BLOCK_PLACE) == null) {
            region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.BLOCK_BREAK) == null) {
            region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.USE) == null) {
            region.setFlag(Flags.USE, StateFlag.State.ALLOW);
        }
        if (region.getFlag(Flags.INTERACT) == null) {
            region.setFlag(Flags.INTERACT, StateFlag.State.ALLOW);
        }
        if (region.getFlag(Flags.CHEST_ACCESS) == null) {
            region.setFlag(Flags.CHEST_ACCESS, StateFlag.State.ALLOW);
        }
        if (region.getFlag(Flags.TNT) == null) {
            region.setFlag(Flags.TNT, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.ENDERDRAGON_BLOCK_DAMAGE) == null) {
            region.setFlag(Flags.ENDERDRAGON_BLOCK_DAMAGE, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.ENDER_BUILD) == null) {
            region.setFlag(Flags.ENDER_BUILD, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.CREEPER_EXPLOSION) == null) {
            region.setFlag(Flags.CREEPER_EXPLOSION, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.BREEZE_WIND_CHARGE) == null) {
            region.setFlag(Flags.BREEZE_WIND_CHARGE, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.GHAST_FIREBALL) == null) {
            region.setFlag(Flags.GHAST_FIREBALL, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.OTHER_EXPLOSION) == null) {
            region.setFlag(Flags.OTHER_EXPLOSION, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.LIGHTER.getRegionGroupFlag()) == null) {
            region.setFlag(Flags.LIGHTER.getRegionGroupFlag(), RegionGroup.MEMBERS);
        }
        if (region.getFlag(Flags.LIGHTER) == null) {
            region.setFlag(Flags.LIGHTER, StateFlag.State.ALLOW);
        }
        if (region.getFlag(Flags.TRAMPLE_BLOCKS) == null) {
            region.setFlag(Flags.TRAMPLE_BLOCKS, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.DESTROY_VEHICLE) == null) {
            region.setFlag(Flags.DESTROY_VEHICLE, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.RAVAGER_RAVAGE) == null) {
            region.setFlag(Flags.RAVAGER_RAVAGE, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.PISTONS) == null) {
            region.setFlag(Flags.PISTONS, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.SCULK_GROWTH) == null) {
            region.setFlag(Flags.SCULK_GROWTH, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.ENTITY_ITEM_FRAME_DESTROY) == null) {
            region.setFlag(Flags.ENTITY_ITEM_FRAME_DESTROY, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.ENTITY_PAINTING_DESTROY) == null) {
            region.setFlag(Flags.ENTITY_PAINTING_DESTROY, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.LAVA_FIRE) == null) {
            region.setFlag(Flags.LAVA_FIRE, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.MYCELIUM_SPREAD) == null) {
            region.setFlag(Flags.MYCELIUM_SPREAD, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.LIGHTNING) == null) {
            region.setFlag(Flags.LIGHTNING, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.WITHER_DAMAGE) == null) {
            region.setFlag(Flags.WITHER_DAMAGE, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.LAVA_FLOW) == null) {
            region.setFlag(Flags.LAVA_FLOW, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.WATER_FLOW) == null) {
            region.setFlag(Flags.WATER_FLOW, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.FIRE_SPREAD) == null) {
            region.setFlag(Flags.FIRE_SPREAD, StateFlag.State.DENY);
        }
        if (region.getFlag(Flags.PVP) == null) {
            region.setFlag(Flags.PVP, StateFlag.State.ALLOW);
        }

        // Add the region
        regions.addRegion(region);
        // Apply config-based flag defaults (only for missing flags)
        applyConfigFlagDefaults(region);
    }

    /**
     * Update deny messages and BUILD flags for all existing regions to silence warnings
     * and fix cross-chunk piston/hopper issues.
     * This should be called when the plugin loads to update all existing regions.
     * Only sets flags that are currently null to preserve mayor/co-owner customizations.
     */
    public static void updateAllRegionDenyMessages() {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

        // Iterate through all worlds
        for (World world : Bukkit.getWorlds()) {
            RegionManager manager = container.get(BukkitAdapter.adapt(world));
            if (manager == null) continue;

            // Update all village regions
            for (ProtectedRegion region : manager.getRegions().values()) {
                if (region.getId().startsWith("village_")) {
                    // Update deny messages only if not already set
                    if (region.getFlag(Flags.DENY_MESSAGE) == null) {
                        region.setFlag(Flags.DENY_MESSAGE, "§7§oThis land is protected by a magical force.");
                    }
                    if (region.getFlag(Flags.ENTRY_DENY_MESSAGE) == null) {
                        region.setFlag(Flags.ENTRY_DENY_MESSAGE, "");
                    }
                    if (region.getFlag(Flags.EXIT_DENY_MESSAGE) == null) {
                        region.setFlag(Flags.EXIT_DENY_MESSAGE, "");
                    }

                    // Update lighter flag for village regions only if not already set
                    if (region.getFlag(Flags.LIGHTER.getRegionGroupFlag()) == null) {
                        region.setFlag(Flags.LIGHTER.getRegionGroupFlag(), RegionGroup.MEMBERS);
                    }
                    if (region.getFlag(Flags.LIGHTER) == null) {
                        region.setFlag(Flags.LIGHTER, StateFlag.State.ALLOW);
                    }

                    // FIX BUILD FLAG - Remove it entirely to let specific flags work
                    region.setFlag(Flags.BUILD.getRegionGroupFlag(), null); // Remove group restriction
                    region.setFlag(Flags.BUILD, null); // Remove BUILD flag entirely

                    // Add specific flags if they don't exist
                    if (region.getFlag(Flags.BLOCK_PLACE) == null) {
                        // Extract village info to determine proper permissions
                        String[] parts = region.getId().split("_");
                        if (parts.length >= 2) {
                            StringBuilder villageNameBuilder = new StringBuilder();
                            for (int i = 1; i < parts.length - 2; i++) {
                                if (i > 1) villageNameBuilder.append("_");
                                villageNameBuilder.append(parts[i]);
                            }
                            String villageName = villageNameBuilder.toString();
                            Village village = VillagesPlugin.getInstance().getVillageManager().getVillageByName(villageName);

                            if (village != null) {
                                // Find the claim for this chunk
                                int cx = Integer.parseInt(parts[parts.length - 2]);
                                int cz = Integer.parseInt(parts[parts.length - 1]);
                                Claim claim = null;
                                for (Claim c : village.getClaims()) {
                                    if (c.getChunk().getX() == cx && c.getChunk().getZ() == cz) {
                                        claim = c;
                                        break;
                                    }
                                }

                                // Set proper block place/break permissions only if not already set
                                if (claim != null && claim.getOwner() != null) {
                                    // Private chunk, members only
                                    if (region.getFlag(Flags.BLOCK_PLACE.getRegionGroupFlag()) == null) {
                                        region.setFlag(Flags.BLOCK_PLACE.getRegionGroupFlag(), RegionGroup.MEMBERS);
                                    }
                                    if (region.getFlag(Flags.BLOCK_PLACE) == null) {
                                        region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.ALLOW);
                                    }
                                    if (region.getFlag(Flags.BLOCK_BREAK.getRegionGroupFlag()) == null) {
                                        region.setFlag(Flags.BLOCK_BREAK.getRegionGroupFlag(), RegionGroup.MEMBERS);
                                    }
                                    if (region.getFlag(Flags.BLOCK_BREAK) == null) {
                                        region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
                                    }
                                } else {
                                    // Village chunk, check village settings
                                    if (village.isAllowMembersToEditUnassignedChunks()) {
                                        if (region.getFlag(Flags.BLOCK_PLACE.getRegionGroupFlag()) == null) {
                                            region.setFlag(Flags.BLOCK_PLACE.getRegionGroupFlag(), RegionGroup.MEMBERS);
                                        }
                                        if (region.getFlag(Flags.BLOCK_PLACE) == null) {
                                            region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.ALLOW);
                                        }
                                        if (region.getFlag(Flags.BLOCK_BREAK.getRegionGroupFlag()) == null) {
                                            region.setFlag(Flags.BLOCK_BREAK.getRegionGroupFlag(), RegionGroup.MEMBERS);
                                        }
                                        if (region.getFlag(Flags.BLOCK_BREAK) == null) {
                                            region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
                                        }
                                    } else {
                                        if (region.getFlag(Flags.BLOCK_PLACE.getRegionGroupFlag()) == null) {
                                            region.setFlag(Flags.BLOCK_PLACE.getRegionGroupFlag(), RegionGroup.OWNERS);
                                        }
                                        if (region.getFlag(Flags.BLOCK_PLACE) == null) {
                                            region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.ALLOW);
                                        }
                                        if (region.getFlag(Flags.BLOCK_BREAK.getRegionGroupFlag()) == null) {
                                            region.setFlag(Flags.BLOCK_BREAK.getRegionGroupFlag(), RegionGroup.OWNERS);
                                        }
                                        if (region.getFlag(Flags.BLOCK_BREAK) == null) {
                                            region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Ensure pistons work cross-chunk only if not already set
                    if (region.getFlag(Flags.PISTONS) == null) {
                        region.setFlag(Flags.PISTONS, StateFlag.State.ALLOW);
                    }
                    region.setFlag(Flags.PISTONS.getRegionGroupFlag(), null); // Remove group restriction

                    // Ensure item transfer works for hoppers only if not already set
                    if (region.getFlag(Flags.ITEM_DROP) == null) {
                        region.setFlag(Flags.ITEM_DROP, StateFlag.State.ALLOW);
                    }
                    if (region.getFlag(Flags.ITEM_PICKUP) == null) {
                        region.setFlag(Flags.ITEM_PICKUP, StateFlag.State.ALLOW);
                    }

                    Bukkit.getLogger().log(Level.INFO, getLogPrefix() + "Updated BUILD flag for region: " + region.getId());
                }

                if (region.getId().startsWith("spawn_")) {
                    // Update deny messages only if not already set
                    if (region.getFlag(Flags.DENY_MESSAGE) == null) {
                        region.setFlag(Flags.DENY_MESSAGE, "");
                    }
                    if (region.getFlag(Flags.ENTRY_DENY_MESSAGE) == null) {
                        region.setFlag(Flags.ENTRY_DENY_MESSAGE, "");
                    }
                    if (region.getFlag(Flags.EXIT_DENY_MESSAGE) == null) {
                        region.setFlag(Flags.EXIT_DENY_MESSAGE, "");
                    }

                    // Update lighter flag for spawn regions only if not already set
                    if (region.getFlag(Flags.LIGHTER.getRegionGroupFlag()) == null) {
                        region.setFlag(Flags.LIGHTER.getRegionGroupFlag(), RegionGroup.MEMBERS);
                    }
                    if (region.getFlag(Flags.LIGHTER) == null) {
                        region.setFlag(Flags.LIGHTER, StateFlag.State.ALLOW);
                    }

                    // Fix BUILD flag for spawn regions, remove it
                    region.setFlag(Flags.BUILD.getRegionGroupFlag(), null);
                    region.setFlag(Flags.BUILD, null);

                    // Set specific protections for spawn only if not already set
                    if (region.getFlag(Flags.BLOCK_PLACE) == null) {
                        region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
                    }
                    if (region.getFlag(Flags.BLOCK_BREAK) == null) {
                        region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
                    }

                    if (region.getFlag(Flags.PISTONS) == null) {
                        region.setFlag(Flags.PISTONS, StateFlag.State.ALLOW);
                    }
                    region.setFlag(Flags.PISTONS.getRegionGroupFlag(), null);
                }
                // Always apply config-based flag defaults last (only for missing flags)
                applyConfigFlagDefaults(region);
            }
        }

        Bukkit.getLogger().log(Level.INFO, getLogPrefix() + "Updated all existing region flags for cross-chunk compatibility");
    }

    public static void logAvailableWorldGuardFlags() {
        var registry = WorldGuard.getInstance().getFlagRegistry();
        StringBuilder sb = new StringBuilder(getLogPrefix() + "Available WorldGuard flags: ");
        boolean first = true;
        for (com.sk89q.worldguard.protection.flags.Flag<?> flag : registry) {
            if (!first) sb.append(", ");
            sb.append(flag.getName());
            first = false;
        }
        Bukkit.getLogger().info(sb.toString());
    }

    @Deprecated
    public static boolean isFlagToggleableByMayor(String flagName) {
        org.bukkit.configuration.ConfigurationSection toggleableSection = VillagesPlugin.getInstance().getConfig().getConfigurationSection("worldguard.mayor_toggleable_flags");
        if (toggleableSection == null) return false;
        return toggleableSection.getBoolean(flagName, false);
    }

    public static String getFlagType(String flagName) {
        var registry = WorldGuard.getInstance().getFlagRegistry();
        com.sk89q.worldguard.protection.flags.Flag<?> flag = registry.get(flagName);
        if (flag == null) return null;
        if (flagName.endsWith("-group")) return "group";
        if (flag instanceof StateFlag) return "state";
        if (flag instanceof com.sk89q.worldguard.protection.flags.EnumFlag) return "enum";
        if (flag instanceof com.sk89q.worldguard.protection.flags.StringFlag) return "string";
        if (flag instanceof com.sk89q.worldguard.protection.flags.IntegerFlag) return "integer";
        if (flag instanceof com.sk89q.worldguard.protection.flags.DoubleFlag) return "double";
        return "unknown";
    }
}