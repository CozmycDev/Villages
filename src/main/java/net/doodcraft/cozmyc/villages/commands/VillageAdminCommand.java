package net.doodcraft.cozmyc.villages.commands;

import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.managers.VillageManager;
import net.doodcraft.cozmyc.villages.models.ChunkCoord;
import net.doodcraft.cozmyc.villages.models.Village;
import net.doodcraft.cozmyc.villages.utils.WGUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VillageAdminCommand implements CommandExecutor {
    private final VillagesPlugin plugin;
    private final VillageManager villageManager;
    private final String separator;

    public VillageAdminCommand(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.villageManager = plugin.getVillageManager();
        this.separator = "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(separator);
            sender.sendMessage("§c§l              ⚠ INSUFFICIENT PERMISSIONS");
            sender.sendMessage(separator);
            sender.sendMessage("");
            sender.sendMessage("§7§o    This command can only be used by server operators.");
            sender.sendMessage("");
            sender.sendMessage(separator);
            return true;
        }

        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadConfig();
                plugin.getBuffManager().loadBuffs();
                plugin.reinitializeAll();
                plugin.getVillageCommand().reloadConfigDependentValues();
                sendSeparator(sender);
                sender.sendMessage("§a§l              ✅ CONFIG & ATTRIBUTE BUFFS RELOADED");
                sendSeparator(sender);
                return true;
            case "claim":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players.");
                    return true;
                }
                handleClaim((Player) sender);
                break;
            case "unclaim":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players.");
                    return true;
                }
                handleUnclaim((Player) sender);
                break;
            case "rename":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /villageadmin rename <newName>");
                    return true;
                }
                handleRename(sender, args[1]);
                break;
            case "info":
                handleInfo(sender);
                break;
            case "delete":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /villageadmin delete <villageName>");
                    return true;
                }
                String targetName = args[1];
                Village targetVillage = villageManager.getVillageByName(targetName);
                if (targetVillage == null || villageManager.isServerVillage(targetVillage.getName())) {
                    sender.sendMessage("§cVillage not found or is the server village.");
                    return true;
                }
                for (var claim : new java.util.HashSet<>(targetVillage.getClaims())) {
                    org.bukkit.World world = org.bukkit.Bukkit.getWorld(claim.getChunk().getWorld());
                    if (world != null) {
                        String regionId = "village_" + targetVillage.getName().toLowerCase() + "_" + claim.getChunk().getX() + "_" + claim.getChunk().getZ();
                        net.doodcraft.cozmyc.villages.utils.WGUtils.deleteRegion(world, regionId);
                    }
                }
                for (java.util.UUID member : targetVillage.getMembers()) {
                    org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(member);
                    if (p != null && p.isOnline()) {
                        p.sendMessage("§cYour village '" + targetVillage.getName().replaceAll("_", " ") + "' has been deleted by an admin.");
                    }
                }
                villageManager.deleteVillageAndRefund(targetVillage.getName());
                plugin.getPersistenceManager().saveAll();
                sender.sendMessage("§aVillage '" + targetVillage.getName().replaceAll("_", " ") + "' deleted.");
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleClaim(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        int cx = loc.getChunk().getX();
        int cz = loc.getChunk().getZ();
        ChunkCoord chunk = new ChunkCoord(world.getName(), cx, cz);

        if (villageManager.isChunkClaimed(chunk)) {
            sendSeparator(player);
            player.sendMessage("§c§l              ⚠ CHUNK CLAIMED");
            sendSeparator(player);
            player.sendMessage("");
            player.sendMessage("§7§o    This chunk is already claimed.");
            player.sendMessage("");
            sendSeparator(player);
            return;
        }

        Village serverVillage = villageManager.getServerVillage();
        if (serverVillage == null) {
            serverVillage = villageManager.createServerVillage();
        }

        villageManager.claimChunk(serverVillage, chunk, null);
        String regionId = "spawn_" + cx + "_" + cz;
        WGUtils.createServerChunkRegion(world, regionId, cx, cz);

        sendSeparator(player);
        player.sendMessage("§a§l              🗺 CHUNK CLAIMED");
        sendSeparator(player);
        player.sendMessage("");
        player.sendMessage("§7§o    Chunk coordinates: §f" + cx + ", " + cz);
        player.sendMessage("§7§o    World: §f" + world.getName());
        player.sendMessage("");
        sendSeparator(player);
    }

    private void handleUnclaim(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        int cx = loc.getChunk().getX();
        int cz = loc.getChunk().getZ();
        ChunkCoord chunk = new ChunkCoord(world.getName(), cx, cz);

        Village serverVillage = villageManager.getServerVillage();
        if (serverVillage == null || !villageManager.isChunkClaimed(chunk)) {
            sendSeparator(player);
            player.sendMessage("§c§l              ⚠ CHUNK NOT CLAIMED");
            sendSeparator(player);
            player.sendMessage("");
            player.sendMessage("§7§o    This chunk is not claimed by the server village.");
            player.sendMessage("");
            sendSeparator(player);
            return;
        }

        villageManager.unclaimChunk(serverVillage, chunk);
        String regionId = "spawn_" + cx + "_" + cz;
        WGUtils.deleteRegion(world, regionId);

        sendSeparator(player);
        player.sendMessage("§a§l              🗺 CHUNK UNCLAIMED");
        sendSeparator(player);
        player.sendMessage("");
        player.sendMessage("§7§o    Chunk coordinates: §f" + cx + ", " + cz);
        player.sendMessage("§7§o    World: §f" + world.getName());
        player.sendMessage("");
        sendSeparator(player);
    }

    private void handleRename(CommandSender sender, String newName) {
        Village serverVillage = villageManager.getServerVillage();
        if (serverVillage == null) {
            serverVillage = villageManager.createServerVillage();
        }

        if (villageManager.isVillageNameTaken(newName)) {
            sendSeparator(sender);
            sender.sendMessage("§c§l              ⚠ NAME TAKEN");
            sendSeparator(sender);
            sender.sendMessage("");
            sender.sendMessage("§7§o    That village name is already taken.");
            sender.sendMessage("");
            sendSeparator(sender);
            return;
        }

        villageManager.renameVillage(serverVillage.getName(), newName);
        sendSeparator(sender);
        sender.sendMessage("§a§l              🏷 VILLAGE RENAMED");
        sendSeparator(sender);
        sender.sendMessage("");
        sender.sendMessage("§7§o    Server village is now called: §f" + newName.replace('_', ' '));
        sender.sendMessage("");
        sendSeparator(sender);
    }

    private void handleInfo(CommandSender sender) {
        Village serverVillage = villageManager.getServerVillage();
        if (serverVillage == null) {
            sendSeparator(sender);
            sender.sendMessage("§c§l              ⚠ NO SERVER VILLAGE");
            sendSeparator(sender);
            sender.sendMessage("");
            sender.sendMessage("§7§o    The server village has not been created yet.");
            sender.sendMessage("§7§o    Claim a chunk to create it.");
            sender.sendMessage("");
            sendSeparator(sender);
            return;
        }

        sendSeparator(sender);
        sender.sendMessage("§a§l              ℹ SERVER VILLAGE INFO");
        sendSeparator(sender);
        sender.sendMessage("");
        sender.sendMessage("§7§o    Name: §f" + serverVillage.getName().replace('_', ' '));
        sender.sendMessage("§7§o    Claims: §f" + serverVillage.getClaims().size());
        sender.sendMessage("");
        sendSeparator(sender);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(separator);
        sender.sendMessage("§6§l                      ⚜ VILLAGE ADMIN COMMANDS ⚜");
        sender.sendMessage(separator);
        sender.sendMessage("");
        sender.sendMessage("§f▶ §b/villageadmin claim §8┃ §7Claim the current chunk for the server village");
        sender.sendMessage("§f▶ §b/villageadmin unclaim §8┃ §7Unclaim the current chunk");
        sender.sendMessage("§f▶ §b/villageadmin rename §8<§7name§8> §8┃ §7Rename the server village");
        sender.sendMessage("§f▶ §b/villageadmin info §8┃ §7View server village information");
        sender.sendMessage("§f▶ §b/villageadmin delete §8<§7villageName§8> §8┃ §7Delete a village by name");
        sender.sendMessage("§f▶ §b/villageadmin reload §8┃ §7Reload plugin config and attribute buffs");
        sender.sendMessage("");
        sender.sendMessage(separator);
    }

    private void sendSeparator(CommandSender sender) {
        sender.sendMessage(separator);
    }
} 