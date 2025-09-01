package net.doodcraft.cozmyc.villages.commands;

import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.managers.EconomyManager;
import net.doodcraft.cozmyc.villages.managers.InviteManager;
import net.doodcraft.cozmyc.villages.managers.VillageManager;
import net.doodcraft.cozmyc.villages.models.ChunkCoord;
import net.doodcraft.cozmyc.villages.models.Claim;
import net.doodcraft.cozmyc.villages.models.Village;
import net.doodcraft.cozmyc.villages.utils.DiscordWebhook;
import net.doodcraft.cozmyc.villages.utils.PlayerNameCache;
import net.doodcraft.cozmyc.villages.utils.WGUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

public class VillageCommand implements CommandExecutor {

    private final VillagesPlugin plugin;
    private final VillageManager villageManager;
    private final String separator;
    private final DiscordWebhook webhook;
    private final Map<UUID, Long> spawnCooldowns = new HashMap<>();
    private long spawnCooldownMillis;
    private final Map<UUID, ChunkCoord> pendingOutpostClaims = new HashMap<>();
    private final Map<UUID, Integer> activeMapToggles = new HashMap<>(); // Track active map toggles
    private final Map<UUID, ChunkCoord> lastMapChunks = new HashMap<>(); // Track last chunk for each player
    private final Map<UUID, Long> pendingDeleteConfirmations = new HashMap<>(); // Track pending delete confirmations
    private static final long DELETE_CONFIRMATION_WINDOW = 30_000;

    public VillageCommand(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.villageManager = plugin.getVillageManager();
        this.separator = plugin.getConfig().getString("messages.separator", "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        String webhookUrl = plugin.getConfig().getString("discord.webhook_url");
        this.webhook = new DiscordWebhook(plugin, webhookUrl);
        reloadConfigDependentValues();
    }

    public void reloadConfigDependentValues() {
        this.spawnCooldownMillis = plugin.getConfig().getLong("village.spawn_cooldown_seconds", 300) * 1000L;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Map<String, String> ph = new HashMap<>();
            sendMessage((Player)sender, "player_only", ph);
            return true;
        }
        Player player = (Player) sender;
        if (args.length >= 1 && args[0].equalsIgnoreCase("create")) {
            if (args.length < 2) {
                Map<String, String> ph = new HashMap<>();
                ph.put("usage", "/village create <name>" + (plugin.getPKBridge().isEnabled() ? " [element]" : ""));
                sendMessage(player, "invalid_usage", ph);
                return true;
            }
            String name = args[1];
            String chosenElement = null;
            if (plugin.getPKBridge().isEnabled() && args.length >= 3) {
                chosenElement = args[2].toLowerCase();
                List<String> valid = plugin.getPKBridge().getMainElements();
                if (!valid.contains(chosenElement)) {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("elements", String.join(", ", valid));
                    sendMessage(player, "invalid_element", ph);
                    return true;
                }
            }
            if (villageManager.isPlayerInVillage(player.getUniqueId())) {
                sendMessage(player, "already_in_village", null);
                return true;
            }
            if (villageManager.isVillageNameTaken(name)) {
                sendMessage(player, "name_taken", null);
                return true;
            }
            double cost = plugin.getConfig().getDouble("village.creation_cost", 192);
            if (VillagesPlugin.getEconomy().getBalance(player) < cost) {
                Map<String, String> ph = new HashMap<>();
                ph.put("cost", String.valueOf(cost));
                ph.put("currency", EconomyManager.getName());
                ph.put("action", "create a village");
                ph.put("balance", String.valueOf(VillagesPlugin.getEconomy().getBalance(player)));
                sendMessage(player, "insufficient_funds", ph);
                return true;
            }
            VillagesPlugin.getEconomy().withdrawPlayer(player, cost);
            Village newVillage = villageManager.createVillage(name, player.getUniqueId());
            newVillage.setVillageCreationCost(cost);
            if (chosenElement != null) {
                villageManager.setVillageElement(name, chosenElement);
            }
            Map<String, String> ph = new HashMap<>();
            ph.put("village", name.replace('_', ' '));
            ph.put("element", newVillage.getElement() != null ? capitalize(newVillage.getElement()) : "");
            sendMessage(player, "village_created", ph);
            String template = plugin.getConfig().getString("discord.messages.village_created", "**%player%** has founded a new village!\nName: %village%%element%");
            String elementStr = newVillage.getElement() != null ? capitalize(newVillage.getElement()) : "";
            String title = plugin.getConfig().getString("discord.titles.village_created", "🏛 New Village Founded");
            title = title
                .replace("%player%", player.getName())
                .replace("%village%", name.replace('_', ' '))
                .replace("%element%", elementStr);
            if (template == null || template.trim().isEmpty()) return true;
            String message = template
                .replace("%player%", player.getName())
                .replace("%village%", name.replace('_', ' '))
                .replace("%element%", elementStr);
            webhook.sendMessage(title, message, plugin.getConfig().getInt("discord.colors.village_created", 65280));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("claim")) {
            handleClaim(player, args);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("invite")) {
            InviteManager im = plugin.getInviteManager();
            String vName = villageManager.getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "invite players");
                sendMessage(player, "not_in_village", ph);
                return true;
            }
            Village village = villageManager.getVillageByName(vName);
            if (!village.getMayor().equals(player.getUniqueId()) && !village.getCoOwners().contains(player.getUniqueId())) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "invite members");
                sendMessage(player, "insufficient_rank", ph);
                return true;
            }
            if (args.length < 2) {
                Map<String, String> ph = new HashMap<>();
                ph.put("usage", "/village invite <player>");
                sendMessage(player, "invalid_usage", ph);
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sendMessage(player, "player_offline", null);
                return true;
            }
            if (villageManager.isPlayerInVillage(target.getUniqueId())) {
                sendMessage(player, "already_in_village", null);
                return true;
            }
            im.addInvite(target.getUniqueId(), vName, player.getUniqueId());
            Map<String, String> ph = new HashMap<>();
            ph.put("player", target.getName());
            sendMessage(player, "invite_sent", ph);
            Map<String, String> ph2 = new HashMap<>();
            ph2.put("village", village.getName().replaceAll("_", " "));
            ph2.put("inviter", player.getDisplayName());
            sendMessage(target, "invite_received", ph2);
            String joinTemplate = plugin.getConfig().getString("discord.messages.village_joined", "**%player%** has joined the village **%village%**");
            String joinTitle = plugin.getConfig().getString("discord.titles.village_joined", "👋 New Village Member");
            joinTitle = joinTitle
                .replace("%player%", player.getName())
                .replace("%village%", village.getName().replace('_', ' '));
            if (joinTemplate != null && !joinTemplate.trim().isEmpty()) {
                webhook.sendMessage(joinTitle,
                    joinTemplate
                        .replace("%player%", player.getName())
                        .replace("%village%", village.getName().replace('_', ' ')),
                    plugin.getConfig().getInt("discord.colors.village_joined", 3447003));
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("accept")) {
            InviteManager im = plugin.getInviteManager();
            Set<String> pendingInvites = im.getPendingInvites(player.getUniqueId());
            if (pendingInvites.isEmpty()) {
                sendMessage(player, "no_invites", null);
                return true;
            }
            if (villageManager.isPlayerInVillage(player.getUniqueId())) {
                sendMessage(player, "already_in_village", null);
                return true;
            }
            if (args.length < 2) {
                Map<String, String> ph = new HashMap<>();
                StringBuilder invitesList = new StringBuilder();
                int index = 1;
                for (String vName : pendingInvites) {
                    UUID inviter = im.getInviter(player.getUniqueId(), vName);
                    String inviterName = PlayerNameCache.getName(inviter);
                    invitesList.append("§8").append(index).append(". §f").append(vName.replaceAll("_", " ")).append(" §7(Invited by: §f").append(inviterName).append("§7)\n");
                    index++;
                }
                ph.put("invites", invitesList.toString());
                sendMessage(player, "pending_invites", ph);
                return true;
            }
            try {
                int inviteIndex = Integer.parseInt(args[1]);
                if (inviteIndex < 1 || inviteIndex > pendingInvites.size()) {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("min", "1");
                    ph.put("max", String.valueOf(pendingInvites.size()));
                    sendMessage(player, "invalid_number", ph);
                    return true;
                }
                String vName = pendingInvites.toArray(new String[0])[inviteIndex - 1];
                if (villageManager.isPlayerInVillage(player.getUniqueId())) {
                    sendMessage(player, "already_in_village", null);
                    im.removeInvite(player.getUniqueId(), vName);
                    return true;
                }
                Village village = villageManager.getVillageByName(vName);
                if (village == null) {
                    sendMessage(player, "village_not_found", null);
                    im.removeInvite(player.getUniqueId(), vName);
                    return true;
                }
                village.getMembers().add(player.getUniqueId());
                im.removeInvite(player.getUniqueId(), vName);
                Map<String, String> ph = new HashMap<>();
                ph.put("village", village.getName().replaceAll("_", " "));
                sendMessage(player, "joined_village", ph);
                villageManager.addPlayerVillageMap(player.getUniqueId(), vName);
                String joinTemplate2 = plugin.getConfig().getString("discord.messages.village_joined", "**%player%** has joined the village **%village%**");
                String joinTitle2 = plugin.getConfig().getString("discord.titles.village_joined", "👋 New Village Member");
                joinTitle2 = joinTitle2
                    .replace("%player%", player.getName())
                    .replace("%village%", village.getName().replace('_', ' '));
                if (joinTemplate2 != null && !joinTemplate2.trim().isEmpty()) {
                    webhook.sendMessage(joinTitle2,
                        joinTemplate2
                            .replace("%player%", player.getName())
                            .replace("%village%", village.getName().replace('_', ' ')),
                        plugin.getConfig().getInt("discord.colors.village_joined", 3447003));
                }
                return true;
            } catch (NumberFormatException e) {
                sendMessage(player, "invalid_number", null);
                return true;
            }
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("assign")) {
            String vName = villageManager.getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "assign chunks");
                sendMessage(player, "not_in_village", ph);
                return true;
            }
            Village village = villageManager.getVillageByName(vName);
            if (!village.getMayor().equals(player.getUniqueId()) && !village.getCoOwners().contains(player.getUniqueId())) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "assign chunks");
                sendMessage(player, "insufficient_rank", ph);
                return true;
            }
            if (args.length < 2) {
                Map<String, String> ph = new HashMap<>();
                ph.put("usage", "/village assign <player>");
                sendMessage(player, "invalid_usage", ph);
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sendMessage(player, "player_offline", null);
                return true;
            }
            if (!village.getMembers().contains(target.getUniqueId())) {
                sendMessage(player, "not_a_member", null);
                return true;
            }
            Location loc = player.getLocation();
            World world = loc.getWorld();
            int cx = loc.getChunk().getX();
            int cz = loc.getChunk().getZ();
            ChunkCoord chunk = new ChunkCoord(world.getName(), cx, cz);
            if (!villageManager.isChunkClaimed(chunk)) {
                sendMessage(player, "chunk_not_claimed", null);
                return true;
            }
            Claim claim = plugin.getClaimManager().getClaim(chunk);
            if (claim == null || !claim.getVillageName().toLowerCase().replaceAll("_", " ").equals(vName.toLowerCase().replaceAll("_", " "))) {
                sendMessage(player, "not_your_claim", null);
                return true;
            }
            claim.setOwner(target.getUniqueId());
            String regionId = "village_" + vName.toLowerCase() + "_" + cx + "_" + cz;
            Set<UUID> allowed = new HashSet<>(claim.getAllowedMembers());
            WGUtils.updateChunkRegionOwnersAndMembers(village, world, regionId, Collections.singleton(target.getUniqueId()), allowed);
            Map<String, String> ph = new HashMap<>();
            ph.put("player", target.getDisplayName());
            sendMessage(player, "chunk_assigned", ph);
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("allow") || args[0].equalsIgnoreCase("disallow"))) {
            String vName = villageManager.getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "manage permissions");
                sendMessage(player, "not_in_village", ph);
                return true;
            }
            if (args.length < 2) {
                Map<String, String> ph = new HashMap<>();
                ph.put("usage", "/village " + args[0].toLowerCase() + " <player>");
                sendMessage(player, "invalid_usage", ph);
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sendMessage(player, "player_not_found", null);
                return true;
            }
            // Prevent targeting mayors or co-owners
            Village village = villageManager.getVillageByName(vName);
            if (target.getUniqueId().equals(village.getMayor()) || village.getCoOwners().contains(target.getUniqueId())) {
                sendMessage(player, "feature_disabled", null);
                return true;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                sendMessage(player, "cannot_target_self", null);
                return true;
            }
            ChunkCoord chunk = new ChunkCoord(player.getWorld().getName(), player.getLocation().getChunk().getX(), player.getLocation().getChunk().getZ());
            Claim claim = plugin.getClaimManager().getClaim(chunk);
            if (claim == null || !claim.getVillageName().toLowerCase().replaceAll("_", " ").equals(vName.toLowerCase().replaceAll("_", " "))) {
                sendMessage(player, "not_your_claim", null);
                return true;
            }
            if (claim.getOwner() != null && !claim.getOwner().equals(player.getUniqueId()) && !villageManager.getVillageByName(vName).getCoOwners().contains(player.getUniqueId())) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "manage permissions");
                sendMessage(player, "insufficient_rank", ph);
                return true;
            }
            if (args[0].equalsIgnoreCase("allow")) {
                claim.allowMember(target.getUniqueId());
                Map<String, String> ph = new HashMap<>();
                ph.put("player", target.getDisplayName());
                sendMessage(player, "permission_granted", ph);
            } else {
                claim.disallowMember(target.getUniqueId());
                Map<String, String> ph = new HashMap<>();
                ph.put("player", target.getDisplayName());
                sendMessage(player, "permission_revoked", ph);
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("setspawn")) {
            handleSetSpawn(player, args);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("spawn")) {
            handleSpawn(player, args);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("unclaim")) {
            String vName = villageManager.getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "unclaim chunks");
                sendMessage(player, "not_in_village", ph);
                return true;
            }
            Village village = villageManager.getVillageByName(vName);
            if (!village.getMayor().equals(player.getUniqueId()) && !village.getCoOwners().contains(player.getUniqueId())) {
                sendMessage(player, "insufficient_rank", null);
                return true;
            }
            ChunkCoord chunk = new ChunkCoord(player.getLocation().getChunk());
            if (!villageManager.isChunkClaimed(chunk)) {
                sendMessage(player, "chunk_not_claimed", null);
                return true;
            }
            Claim claim = plugin.getClaimManager().getClaim(chunk);
            if (claim == null) {
                sendMessage(player, "not_your_claim", null);
                return true;
            }
            boolean unclaimed = villageManager.unclaimChunk(village, chunk);
            if (!unclaimed) {
                // Check if this is the last main area claim and there are outposts
                if (claim.isMainArea()) {
                    int mainClaims = 0;
                    for (Claim c : village.getClaims()) if (c.isMainArea()) mainClaims++;
                    if (mainClaims == 1) {
                        for (Claim c : village.getClaims()) {
                            if (!c.isMainArea()) {
                                sendMessage(player, "could_not_unclaim_main_with_outposts", null);
                                return true;
                            }
                        }
                    }
                }
                sendMessage(player, "could_not_unclaim_chunk", null);
                return true;
            }
            VillagesPlugin.getEconomy().depositPlayer(player, claim.getCostPaid());
            Map<String, String> ph = new HashMap<>();
            ph.put("x", String.valueOf(chunk.getX()));
            ph.put("z", String.valueOf(chunk.getZ()));
            ph.put("world", chunk.getWorld());
            ph.put("refund", String.valueOf(claim.getCostPaid()));
            sendMessage(player, "chunk_unclaimed", ph);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("unassign")) {
            String vName = villageManager.getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "unassign chunks");
                sendMessage(player, "not_in_village", ph);
                return true;
            }
            Village village = villageManager.getVillageByName(vName);
            if (!village.getMayor().equals(player.getUniqueId()) && !village.getCoOwners().contains(player.getUniqueId())) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "unassign chunks");
                sendMessage(player, "insufficient_rank", ph);
                return true;
            }
            ChunkCoord chunk = new ChunkCoord(player.getLocation().getChunk());
            Claim claim = plugin.getClaimManager().getClaim(chunk);
            if (claim == null || !claim.getVillageName().toLowerCase().replaceAll("_", " ").equals(vName.toLowerCase().replaceAll("_", " "))) {
                sendMessage(player, "not_your_claim", null);
                return true;
            }
            if (claim.getOwner() == null) {
                sendMessage(player, "chunk_unassigned", null);
                return true;
            }
            claim.setOwner(null);
            String regionId = "village_" + vName.toLowerCase() + "_" + chunk.getX() + "_" + chunk.getZ();
            Set<UUID> allowed = new HashSet<>(claim.getAllowedMembers());
            WGUtils.updateChunkRegionOwnersAndMembers(village, player.getWorld(), regionId, Collections.emptySet(), allowed);
            sendMessage(player, "chunk_unassigned", null);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("rename")) {
            String vName = villageManager.getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "rename it");
                sendMessage(player, "not_in_village", ph);
                return true;
            }
            Village village = villageManager.getVillageByName(vName);
            if (!village.getMayor().equals(player.getUniqueId())) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "rename the village");
                sendMessage(player, "insufficient_rank", ph);
                return true;
            }
            if (args.length < 2) {
                Map<String, String> ph = new HashMap<>();
                ph.put("usage", "/village rename <newName>");
                sendMessage(player, "invalid_usage", ph);
                return true;
            }
            String newName = args[1];
            if (villageManager.isVillageNameTaken(newName)) {
                sendMessage(player, "name_taken", null);
                return true;
            }
            double renameCost = plugin.getConfig().getDouble("village.rename_cost", 0);
            if (VillagesPlugin.getEconomy().getBalance(player) < renameCost) {
                Map<String, String> ph = new HashMap<>();
                ph.put("cost", String.valueOf(renameCost));
                ph.put("currency", EconomyManager.getName());
                ph.put("action", "rename your village");
                ph.put("balance", String.valueOf(VillagesPlugin.getEconomy().getBalance(player)));
                sendMessage(player, "insufficient_funds", ph);
                return true;
            }
            VillagesPlugin.getEconomy().withdrawPlayer(player, renameCost);
            villageManager.renameVillage(vName, newName);
            Map<String, String> ph = new HashMap<>();
            ph.put("new_name", newName.replace('_', ' '));
            sendMessage(player, "village_renamed", ph);
            String renamedTitle = plugin.getConfig().getString("discord.titles.village_renamed", "🏷 Village Renamed");
            renamedTitle = renamedTitle
                .replace("%old_village%", vName.replace('_', ' '))
                .replace("%new_village%", newName.replace('_', ' '))
                .replace("%mayor%", player.getName());
            String renamedTemplate = plugin.getConfig().getString("discord.messages.village_renamed", "Village **%old_village%** has been renamed to **%new_village%**\nMayor: %mayor%");
            if (renamedTemplate != null && !renamedTemplate.trim().isEmpty()) {
                webhook.sendMessage(renamedTitle,
                    renamedTemplate
                        .replace("%old_village%", vName.replace('_', ' '))
                        .replace("%new_village%", newName.replace('_', ' '))
                        .replace("%mayor%", player.getName()),
                    plugin.getConfig().getInt("discord.colors.village_renamed", 16776960));
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("element")) {
            if (!plugin.getPKBridge().isEnabled()) {
                sendMessage(player, "feature_disabled", null);
                return true;
            }
            String vName = villageManager.getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "set an element");
                sendMessage(player, "not_in_village", ph);
                return true;
            }
            Village village = villageManager.getVillageByName(vName);
            if (!village.getMayor().equals(player.getUniqueId())) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "set the village element");
                sendMessage(player, "insufficient_rank", ph);
                return true;
            }
            boolean allowElementChange = plugin.getConfig().getBoolean("village.allow_element_change", false);
            if (village.getElement() != null && !allowElementChange) {
                Map<String, String> ph = new HashMap<>();
                ph.put("element", village.getElement());
                sendMessage(player, "element_already_set", ph);
                return true;
            }
            if (args.length < 2) {
                Map<String, String> ph = new HashMap<>();
                ph.put("usage", "/village element <element>");
                ph.put("elements", String.join(", ", plugin.getPKBridge().getMainElements()));
                sendMessage(player, "invalid_usage", ph);
                return true;
            }
            String chosen = args[1].toLowerCase();
            List<String> valid = plugin.getPKBridge().getMainElements();
            if (!valid.contains(chosen)) {
                Map<String, String> ph = new HashMap<>();
                ph.put("elements", String.join(", ", valid));
                sendMessage(player, "invalid_element", ph);
                return true;
            }
            villageManager.setVillageElement(vName, chosen);
            Map<String, String> ph = new HashMap<>();
            ph.put("element", chosen);
            sendMessage(player, "element_chosen", ph);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("map")) {
            handleMap(player, args);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("delete")) {
            String vName = villageManager.getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "delete your village");
                sendMessage(player, "not_in_village", ph);
                return true;
            }
            Village village = villageManager.getVillageByName(vName);
            if (!village.getMayor().equals(player.getUniqueId())) {
                sendMessage(player, "insufficient_rank", null);
                return true;
            }
            long now = System.currentTimeMillis();
            Long lastRequest = pendingDeleteConfirmations.get(player.getUniqueId());
            if (lastRequest == null || now - lastRequest > DELETE_CONFIRMATION_WINDOW) {
                pendingDeleteConfirmations.put(player.getUniqueId(), now);
                sendMessage(player, "confirm_village_deletion", null);
                return true;
            }
            // Proceed with deletion
            pendingDeleteConfirmations.remove(player.getUniqueId());
            double refund = villageManager.deleteVillageAndRefund(vName);
            VillagesPlugin.getEconomy().depositPlayer(player, refund);
            Map<String, String> ph = new HashMap<>();
            ph.put("village", vName.replace('_', ' '));
            ph.put("mayor", player.getName());
            ph.put("refund", String.valueOf(refund));
            sendMessage(player, "village_deleted", ph);
            // Discord notification
            String template = plugin.getConfig().getString("discord.messages.village_deleted", "Village **%village%** has fallen into ruins.\nMayor: %mayor%");
            String title = plugin.getConfig().getString("discord.titles.village_deleted", "\ud83c\udfd3 Village Deleted");
            title = title.replace("%village%", vName.replace('_', ' ')).replace("%mayor%", player.getName());
            if (template != null && !template.trim().isEmpty()) {
                webhook.sendMessage(title,
                    template.replace("%village%", vName.replace('_', ' ')).replace("%mayor%", player.getName()),
                    plugin.getConfig().getInt("discord.colors.village_deleted", 16711680));
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("info")) {
            String vName = args.length >= 2 ? args[1] : villageManager.getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                sendMessage(player, "no_village_info", null);
                return true;
            }
            Village village = villageManager.getVillageByName(vName);
            if (village == null) {
                sendMessage(player, "no_village_info", null);
                return true;
            }
            Map<String, String> ph = new HashMap<>();
            ph.put("name", village.getName().replace('_', ' '));
            ph.put("mayor", PlayerNameCache.getName(village.getMayor()));
            ph.put("members", String.valueOf(village.getMembers().size()));
            ph.put("claims", String.valueOf(village.getClaims().size()));
            ph.put("element", village.getElement() != null ? capitalize(village.getElement()) : "");
            ph.put("allow_edit", village.isAllowMembersToEditUnassignedChunks() ? "Yes" : "No");
            sendMessage(player, "village_info", ph);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            List<String> all = villageManager.getAllVillageNames().stream().toList();
            if (all.isEmpty()) {
                sendMessage(player, "no_villages_exist", null);
                return true;
            }
            int page = 1;
            int perPage = 8;
            if (args.length >= 2) {
                try { page = Math.max(1, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) {}
            }
            int totalPages = (int) Math.ceil((double) all.size() / perPage);
            page = Math.min(page, totalPages == 0 ? 1 : totalPages);
            Map<String, String> ph = new HashMap<>();
            ph.put("page", String.valueOf(page));
            ph.put("total_pages", String.valueOf(Math.max(1, totalPages)));
            sendSeparator(player);
            String header = plugin.getConfig().getString("messages.village_list_header.title", "");
            if (header != null && !header.isEmpty()) player.sendMessage(applyPlaceholders(header, ph));
            player.sendMessage(" ");
            String desc = plugin.getConfig().getString("messages.village_list_header.description", "");
            if (desc != null && !desc.isEmpty()) player.sendMessage(applyPlaceholders(desc, ph));
            player.sendMessage(" ");
            int start = (page - 1) * perPage;
            int end = Math.min(start + perPage, all.size());
            for (int i = start; i < end; i++) {
                Village v = villageManager.getVillageByName(all.get(i));
                if (v == null) continue;
                Map<String, String> eph = new HashMap<>();
                eph.put("name", v.getName().replace('_', ' '));
                eph.put("element", v.getElement() != null ? " (" + capitalize(v.getElement()) + ")" : "");
                eph.put("population", String.valueOf(v.getMembers().size()));
                eph.put("mayor", PlayerNameCache.getName(v.getMayor()));
                eph.put("claims", String.valueOf(v.getClaims().size()));
                String entry = plugin.getConfig().getString("messages.village_list_entry.description", "");
                if (entry != null && !entry.isEmpty()) player.sendMessage(applyPlaceholders(entry, eph));
            }
            if (totalPages > 1) {
                Map<String, String> navPh = new HashMap<>();
                navPh.put("nav", "Page " + page + "/" + totalPages + " - /village list <page>");
                String nav = plugin.getConfig().getString("messages.village_list_nav.description", "");
                if (nav != null && !nav.isEmpty()) player.sendMessage(applyPlaceholders(nav, navPh));
            }
            sendSeparator(player);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("leave")) {
            String vName = villageManager.getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "leave one");
                sendMessage(player, "not_in_village", ph);
                return true;
            }
            Village village = villageManager.getVillageByName(vName);
            if (village.getMayor().equals(player.getUniqueId())) {
                sendMessage(player, "mayor_cannot_leave", null);
                return true;
            }
            village.getMembers().remove(player.getUniqueId());
            villageManager.removePlayerVillageMap(player.getUniqueId());
            Map<String, String> ph = new HashMap<>();
            ph.put("village", village.getName().replace('_', ' '));
            sendMessage(player, "left_village", ph);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("promote")) {
            String vName = villageManager.getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "promote members");
                sendMessage(player, "not_in_village", ph);
                return true;
            }
            Village village = villageManager.getVillageByName(vName);
            if (!village.getMayor().equals(player.getUniqueId())) {
                sendMessage(player, "insufficient_rank", null);
                return true;
            }
            if (args.length < 2) {
                Map<String, String> ph = new HashMap<>();
                ph.put("usage", "/village promote <player>");
                sendMessage(player, "invalid_usage", ph);
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !village.getMembers().contains(target.getUniqueId())) {
                sendMessage(player, "not_a_member", null);
                return true;
            }
            if (village.getCoOwners().contains(target.getUniqueId())) {
                sendMessage(player, "already_coowner", null);
                return true;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                sendMessage(player, "feature_disabled", null);
                return true;
            }
            village.getCoOwners().add(target.getUniqueId());
            Map<String, String> ph = new HashMap<>();
            ph.put("player", target.getName());
            sendMessage(player, "member_promoted", ph);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("demote")) {
            String vName = villageManager.getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "demote members");
                sendMessage(player, "not_in_village", ph);
                return true;
            }
            Village village = villageManager.getVillageByName(vName);
            // Only mayors can demote
            if (!village.getMayor().equals(player.getUniqueId())) {
                sendMessage(player, "insufficient_rank", null);
                return true;
            }
            if (args.length < 2) {
                Map<String, String> ph = new HashMap<>();
                ph.put("usage", "/village demote <player>");
                sendMessage(player, "invalid_usage", ph);
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sendMessage(player, "player_not_found", null);
                return true;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                sendMessage(player, "feature_disabled", null); // You cannot demote yourself
                return true;
            }
            if (!village.getCoOwners().contains(target.getUniqueId())) {
                sendMessage(player, "not_a_member", null); // Not a co-owner
                return true;
            }
            village.getCoOwners().remove(target.getUniqueId());
            Map<String, String> ph = new HashMap<>();
            ph.put("player", target.getName());
            ph.put("village", village.getName().replace('_', ' '));
            sendMessage(player, "member_demoted", ph);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("remove")) {
            String vName = villageManager.getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "kick a member");
                sendMessage(player, "not_in_village", ph);
                return true;
            }
            Village village = villageManager.getVillageByName(vName);
            if (!village.getMayor().equals(player.getUniqueId()) && !village.getCoOwners().contains(player.getUniqueId())) {
                sendMessage(player, "insufficient_rank", null);
                return true;
            }
            if (args.length < 2) {
                Map<String, String> ph = new HashMap<>();
                ph.put("usage", "/village remove <player>");
                sendMessage(player, "invalid_usage", ph);
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !village.getMembers().contains(target.getUniqueId())) {
                sendMessage(player, "not_a_member", null);
                return true;
            }
            if (village.getMayor().equals(target.getUniqueId())) {
                sendMessage(player, "mayor_cannot_leave", null);
                return true;
            }
            village.getMembers().remove(target.getUniqueId());
            village.getCoOwners().remove(target.getUniqueId());
            villageManager.removePlayerVillageMap(target.getUniqueId());
            Map<String, String> ph = new HashMap<>();
            ph.put("player", target.getName());
            ph.put("village", village.getName().replace('_', ' '));
            sendMessage(player, "member_removed", ph);
            sendMessage(target, "member_removed", ph);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("flag")) {
            String vName = villageManager.getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("action", "change flags");
                sendMessage(player, "not_in_village", ph);
                return true;
            }
            Village village = villageManager.getVillageByName(vName);
            if (!village.getMayor().equals(player.getUniqueId()) && !village.getCoOwners().contains(player.getUniqueId())) {
                sendMessage(player, "insufficient_rank", null);
                return true;
            }
            if (args.length < 3) {
                org.bukkit.configuration.ConfigurationSection toggleableSection = plugin.getConfig().getConfigurationSection("worldguard.mayor_toggleable_flags");
                List<String> flags = new ArrayList<>();
                if (toggleableSection != null) {
                    for (String key : toggleableSection.getKeys(false)) {
                        if (toggleableSection.getBoolean(key, false)) flags.add(key);
                    }
                }
                Map<String, String> ph = new HashMap<>();
                ph.put("flags", String.join(", ", getFlagsListForPlayerChunk(player, village)));
                sendMessage(player, "flag_usage", ph);
                return true;
            }
            String flag = args[1];
            String type = WGUtils.getFlagType(flag.replace("-group", ""));
            if (flag.endsWith("-group")) type = "group";
            if (type == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("flag", flag);
                sendMessage(player, "flag_not_toggleable", ph);
                return true;
            }
            String value;
            String areaArg = "1x1";
            if ("string".equals(type)) {
                StringBuilder sb = new StringBuilder();
                boolean inQuotes = false;
                for (int i = 2; i < args.length; i++) {
                    String arg = args[i];
                    if (arg.startsWith("\"") && !inQuotes) {
                        inQuotes = true;
                        sb.append(arg.substring(1));
                    } else if (arg.endsWith("\"") && inQuotes) {
                        sb.append(" ").append(arg, 0, arg.length() - 1);
                        inQuotes = false;
                        // If there is another arg after the closing quote, treat it as area
                        if (i + 1 < args.length) {
                            areaArg = args[i + 1];
                        }
                        break;
                    } else if (inQuotes) {
                        sb.append(" ").append(arg);
                    } else {
                        // Not in quotes, treat as area if it's the last arg
                        if (i == args.length - 1) {
                            areaArg = arg;
                        }
                    }
                }
                value = sb.toString();
                if (value == null || value.isEmpty()) {
                    // fallback: if no quotes, treat next arg as value
                    value = args.length > 2 ? args[2] : "";
                }
            } else {
                value = args[2];
                if (args.length >= 4) {
                    areaArg = args[3];
                }
            }
            int areaSize = 1;
            if (areaArg.equalsIgnoreCase("3x3")) areaSize = 3;
            else if (areaArg.equalsIgnoreCase("4x4")) areaSize = 4;
            else if (!areaArg.equalsIgnoreCase("1x1")) {
                org.bukkit.configuration.ConfigurationSection toggleableSection = plugin.getConfig().getConfigurationSection("worldguard.mayor_toggleable_flags");
                List<String> flags = new ArrayList<>();
                if (toggleableSection != null) {
                    for (String key : toggleableSection.getKeys(false)) {
                        if (toggleableSection.getBoolean(key, false)) flags.add(key);
                    }
                }
                Map<String, String> ph = new HashMap<>();
                ph.put("flags", String.join(", ",  getFlagsListForPlayerChunk(player, village)));
                sendMessage(player, "flag_usage", ph);
                return true;
            }
            boolean valid = false;
            switch (type) {
                case "group":
                    valid = value.equalsIgnoreCase("none") || value.equalsIgnoreCase("members") || value.equalsIgnoreCase("owners") || value.equalsIgnoreCase("all");
                    break;
                case "state":
                    valid = value.equalsIgnoreCase("allow") || value.equalsIgnoreCase("deny") || value.equalsIgnoreCase("none");
                    break;
                case "enum":
                    valid = true; // Accept any value, let WG handle error
                    break;
                case "string":
                    valid = true;
                    break;
                case "integer":
                    try { Integer.parseInt(value); valid = true; } catch (Exception ignored) {}
                    break;
                case "double":
                    try { Double.parseDouble(value); valid = true; } catch (Exception ignored) {}
                    break;
            }
            if (!valid) {
                Map<String, String> ph = new HashMap<>();
                ph.put("flag", flag);
                sendMessage(player, "flag_invalid_value", ph);
                return true;
            }
            // Determine area and batch update
            Location loc = player.getLocation();
            World world = loc.getWorld();
            int centerX = loc.getChunk().getX();
            int centerZ = loc.getChunk().getZ();
            int half = areaSize / 2;
            int updated = 0;
            com.sk89q.worldguard.protection.managers.RegionManager manager = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer().get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    int cx = centerX + dx;
                    int cz = centerZ + dz;
                    ChunkCoord chunk = new ChunkCoord(world.getName(), cx, cz);
                    if (!villageManager.isChunkClaimed(chunk)) continue;
                    String claimVillage = villageManager.getVillageNameByChunk(chunk);
                    if (claimVillage == null || !claimVillage.equalsIgnoreCase(vName)) continue;
                    String regionId = "village_" + vName.toLowerCase() + "_" + cx + "_" + cz;
                    if (manager == null) continue;
                    com.sk89q.worldguard.protection.regions.ProtectedRegion region = manager.getRegion(regionId);
                    if (region == null) continue;
                    com.sk89q.worldguard.protection.flags.Flag<?> flagObj = com.sk89q.worldguard.WorldGuard.getInstance().getFlagRegistry().get(flag.replace("-group", ""));
                    if (flag.endsWith("-group")) {
                        if (flagObj != null && flagObj.getRegionGroupFlag() != null) {
                            try {
                                com.sk89q.worldguard.protection.flags.RegionGroup group = com.sk89q.worldguard.protection.flags.RegionGroup.valueOf(value.toUpperCase());
                                region.setFlag(flagObj.getRegionGroupFlag(), group);
                                updated++;
                            } catch (IllegalArgumentException ignored) {}
                        }
                    } else if (flagObj != null) {
                        String type2 = WGUtils.getFlagType(flag);
                        try {
                            switch (type2) {
                                case "state":
                                    com.sk89q.worldguard.protection.flags.StateFlag.State state = null;
                                    if (value.equalsIgnoreCase("allow")) state = com.sk89q.worldguard.protection.flags.StateFlag.State.ALLOW;
                                    else if (value.equalsIgnoreCase("deny")) state = com.sk89q.worldguard.protection.flags.StateFlag.State.DENY;
                                    else if (value.equalsIgnoreCase("none") || value.equalsIgnoreCase("null")) state = null;
                                    region.setFlag((com.sk89q.worldguard.protection.flags.StateFlag) flagObj, state);
                                    updated++;
                                    break;
                                case "enum":
                                    com.sk89q.worldguard.protection.flags.EnumFlag enumFlag = (com.sk89q.worldguard.protection.flags.EnumFlag) flagObj;
                                    Object parsed = enumFlag.unmarshal(value);
                                    region.setFlag(enumFlag, parsed);
                                    updated++;
                                    break;
                                case "string":
                                    region.setFlag((com.sk89q.worldguard.protection.flags.StringFlag) flagObj, value);
                                    updated++;
                                    break;
                                case "integer":
                                    region.setFlag((com.sk89q.worldguard.protection.flags.IntegerFlag) flagObj, Integer.valueOf(value));
                                    updated++;
                                    break;
                                case "double":
                                    region.setFlag((com.sk89q.worldguard.protection.flags.DoubleFlag) flagObj, Double.valueOf(value));
                                    updated++;
                                    break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            Map<String, String> ph = new HashMap<>();
            ph.put("flag", flag);
            ph.put("value", value);
            ph.put("count", String.valueOf(updated));
            ph.put("area", areaArg);
            if (updated > 0) {
                sendMessage(player, "flag_batch_success", ph);
            } else {
                sendMessage(player, "flag_batch_none", ph);
            }
            return true;
        }

        Map<String, String> helpPh = new HashMap<>();
        helpPh.put("mayor_commands", "  §f▶ §b/village create §8<§7name§8> §8┃ §7Create a new village\n  §f▶ §b/village delete §8┃ §7Delete your village\n  §f▶ §b/village rename §8<§7name§8> §8┃ §7Rename your village\n  §f▶ §b/village promote §8<§7player§8> §8┃ §7Promote member to co-owner\n  §f▶ §b/village demote §8<§7player§8> §8┃ §7Demote co-owner to member\n  §f▶ §b/village togglememberedit §8┃ §7Toggle member editing rights\n  §f▶ §b/village element §8<§7element§8> §8┃ §7Set your village's element");
        helpPh.put("mayor_coowner_commands", "  §f▶ §b/village invite §8<§7player§8> §8┃ §7Invite a player to village\n  §f▶ §b/village remove §8<§7player§8> §8┃ §7Remove member from village\n  §f▶ §b/village assign §8<§7player§8> §8┃ §7Assign current chunk to member\n  §f▶ §b/village unassign §8┃ §7Return chunk to village ownership\n  §f▶ §b/village claim §8┃ §7Claim current chunk\n  §f▶ §b/village unclaim §8┃ §7Unclaim current chunk\n  §f▶ §b/village setspawn §8┃ §7Set village spawn point");
        helpPh.put("chunk_owner_commands", "  §f▶ §b/village allow §8<§7player§8> §8┃ §7Grant chunk editing permission\n  §f▶ §b/village disallow §8<§7player§8> §8┃ §7Revoke chunk editing permission");
        helpPh.put("member_commands", "  §f▶ §b/village leave §8┃ §7Leave your current village\n  §f▶ §b/village spawn §8┃ §7Teleport to village spawn");
        helpPh.put("general_commands", "  §f▶ §b/village accept §8┃ §7Accept pending village invite\n  §f▶ §b/village info §8┃ §7View village information\n  §f▶ §b/village list §8┃ §7Browse all existing villages\n  §f▶ §b/village map [size/toggle] §8┃ §7View/toggle a map of nearby claims");
        sendMessage(player, "help", helpPh);
        return true;
    }
    
    private void sendSeparator(Player player) {
        player.sendMessage(separator);
    }

    private void handleMap(Player player, String[] args) {
        // Handle toggle command
        if (args.length > 1 && args[1].equalsIgnoreCase("toggle")) {
            if (activeMapToggles.containsKey(player.getUniqueId())) {
                // Disable toggle
                plugin.getServer().getScheduler().cancelTask(activeMapToggles.get(player.getUniqueId()));
                activeMapToggles.remove(player.getUniqueId());
                lastMapChunks.remove(player.getUniqueId());
                sendMessage(player, "map_toggle_disabled", null);
            } else {
                // Enable toggle
                int taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                    if (!player.isOnline()) {
                        plugin.getServer().getScheduler().cancelTask(activeMapToggles.get(player.getUniqueId()));
                        activeMapToggles.remove(player.getUniqueId());
                        lastMapChunks.remove(player.getUniqueId());
                        return;
                    }

                    ChunkCoord currentChunk = new ChunkCoord(player.getLocation().getChunk());
                    ChunkCoord lastChunk = lastMapChunks.get(player.getUniqueId());

                    if (lastChunk == null || !lastChunk.equals(currentChunk)) {
                        lastMapChunks.put(player.getUniqueId(), currentChunk);
                        displayMap(player, currentChunk, 9);
                    }
                }, 0L, 20L);

                activeMapToggles.put(player.getUniqueId(), taskId);

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (activeMapToggles.containsKey(player.getUniqueId())) {
                        plugin.getServer().getScheduler().cancelTask(activeMapToggles.get(player.getUniqueId()));
                        activeMapToggles.remove(player.getUniqueId());
                        lastMapChunks.remove(player.getUniqueId());
                        if (player.isOnline()) {
                            sendMessage(player, "map_toggle_expired", null);
                        }
                    }
                }, 2400L);

                sendMessage(player, "map_toggle_enabled", null);
            }
            return;
        }

        // Regular map display
        ChunkCoord centerChunk = new ChunkCoord(player.getLocation().getChunk());
        int size = 31;
        if (args.length > 1) {
            try {
                size = Integer.parseInt(args[1]);
                size = Math.min(Math.max(size, 5), 31);
            } catch (NumberFormatException e) {
                Map<String, String> ph = new HashMap<>();
                ph.put("default", "12");
                sendMessage(player, "invalid_map_size", ph);
            }
        }
        displayMap(player, centerChunk, size);
    }

    private void displayMap(Player player, ChunkCoord centerChunk, int size) {
        int halfSize = size / 2;
        int startX = centerChunk.getX() - halfSize;
        int startZ = centerChunk.getZ() - halfSize;

        // Top separator
        sendSeparator(player);

        // Legend (all legend lines, no extra separators)
        Set<String> shownVillages = new HashSet<>();
        Set<UUID> shownOwners = new HashSet<>();
        // Title
        player.sendMessage(applyPlaceholders(plugin.getConfig().getString("messages.map_legend_title", ""), null));
        // position
        player.sendMessage(applyPlaceholders(plugin.getConfig().getString("messages.map_legend_your_position", ""), null));
        // Unclaimed
        player.sendMessage(applyPlaceholders(plugin.getConfig().getString("messages.map_legend_unclaimed", ""), null));
        // Villages and owners
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                ChunkCoord chunk = new ChunkCoord(centerChunk.getWorld(), startX + x, startZ + z);
                String villageName = plugin.getVillageManager().getVillageNameByChunk(chunk);
                if (villageName != null) {
                    Claim claim = plugin.getClaimManager().getClaim(chunk);
                    if (claim != null && claim.getOwner() != null && !shownOwners.contains(claim.getOwner())) {
                        shownOwners.add(claim.getOwner());
                        String color = getOwnerColor(claim.getOwner()).replace("§x", "");
                        String legend = plugin.getConfig().getString("messages.map_legend_owner", "");
                        legend = legend.replace("%color%", color)
                                .replace("%village%", villageName.replaceAll("_", " "))
                                .replace("%owner%", PlayerNameCache.getName(claim.getOwner()));
                        player.sendMessage(legend);
                    } else if (!shownVillages.contains(villageName)) {
                        shownVillages.add(villageName);
                        String color = getVillageColor(villageName).replace("§x", "");
                        String legend = plugin.getConfig().getString("messages.map_legend_village", "");
                        legend = legend.replace("%color%", color)
                                .replace("%village%", villageName.replaceAll("_", " "));
                        player.sendMessage(legend);
                    }
                }
            }
        }

        // Map content
        for (int z = 0; z < size; z++) {
            StringBuilder line = new StringBuilder();
            for (int x = 0; x < size; x++) {
                ChunkCoord chunk = new ChunkCoord(centerChunk.getWorld(), startX + x, startZ + z);
                String villageName = plugin.getVillageManager().getVillageNameByChunk(chunk);
                if (chunk.equals(centerChunk)) {
                    line.append(" §f■");
                } else if (villageName != null) {
                    Claim claim = plugin.getClaimManager().getClaim(chunk);
                    if (claim != null && claim.getOwner() != null) {
                        String color = getOwnerColor(claim.getOwner());
                        line.append(color + " ■");
                    } else {
                        String color = getVillageColor(villageName);
                        line.append(color + " ■");
                    }
                } else {
                    line.append(" §8■");
                }
            }
            player.sendMessage(line.toString());
        }

        sendSeparator(player);
    }

    private String getOwnerColor(UUID ownerId) {
        // Generate a consistent color based on the owner's UUID
        int hash = ownerId.hashCode();
        // Use HSL
        float hue = (hash & 0xFF) / 255f * 0.3f + 0.3f; // 0.3-0.6
        float saturation = 0.8f + ((hash >> 8) & 0xFF) / 255f * 0.2f; // 0.8-1.0
        float lightness = 0.5f + ((hash >> 16) & 0xFF) / 255f * 0.1f; // 0.5-0.6

        float r, g, b;
        if (saturation == 0f) {
            r = g = b = lightness;
        } else {
            float q = lightness < 0.5f ? lightness * (1f + saturation) : lightness + saturation - lightness * saturation;
            float p = 2f * lightness - q;
            r = hueToRgb(p, q, hue + 1f/3f);
            g = hueToRgb(p, q, hue);
            b = hueToRgb(p, q, hue - 1f/3f);
        }

        String hexColor = String.format("#%02x%02x%02x", 
            (int)(r * 255), 
            (int)(g * 255), 
            (int)(b * 255));
        
        return "§x" + hexColor.substring(1).replaceAll("(.)", "§$1");
    }

    private String getVillageColor(String villageName) {
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
            r = hueToRgb(p, q, hue + 1f/3f);
            g = hueToRgb(p, q, hue);
            b = hueToRgb(p, q, hue - 1f/3f);
        }

        String hexColor = String.format("#%02x%02x%02x", 
            (int)(r * 255), 
            (int)(g * 255), 
            (int)(b * 255));
        
        return "§x" + hexColor.substring(1).replaceAll("(.)", "§$1");
    }

    private float hueToRgb(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f/6f) return p + (q - p) * 6f * t;
        if (t < 1f/2f) return q;
        if (t < 2f/3f) return p + (q - p) * (2f/3f - t) * 6f;
        return p;
    }

    private void handleSpawn(Player player, String[] args) {
        String villageName = villageManager.getVillageNameByPlayer(player.getUniqueId());
        if (villageName == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("action", "teleport to its spawn");
            sendMessage(player, "not_in_village", ph);
            return;
        }
        Village village = villageManager.getVillageByName(villageName);
        int outpostIndex = -1;
        if (args.length >= 2) {
            try {
                outpostIndex = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                Map<String, String> ph = new HashMap<>();
                ph.put("index", args[1]);
                sendMessage(player, "invalid_outpost_index", ph);
                return;
            }
        }
        long currentTime = System.currentTimeMillis();
        Long lastSpawn = spawnCooldowns.get(player.getUniqueId());
        if (lastSpawn != null) {
            long timeLeft = spawnCooldownMillis - (currentTime - lastSpawn);
            if (timeLeft > 0) {
                Map<String, String> ph = new HashMap<>();
                ph.put("seconds", String.valueOf(timeLeft / 1000));
                sendMessage(player, "spawn_cooldown", ph);
                return;
            }
        }
        if (outpostIndex == -1) {
            // Main spawn
            if (!village.hasSpawnSet()) {
                sendMessage(player, "main_spawn_not_set", null);
                return;
            }
            player.teleport(village.getSpawnLocation());
            spawnCooldowns.put(player.getUniqueId(), currentTime);
            sendMessage(player, "teleported_to_spawn", null);
        } else {
            if (!village.getOutpostIndices().contains(outpostIndex)) {
                Map<String, String> ph = new HashMap<>();
                ph.put("index", String.valueOf(outpostIndex));
                sendMessage(player, "invalid_outpost_index", ph);
                return;
            }
            if (!village.hasOutpostSpawn(outpostIndex)) {
                Map<String, String> ph = new HashMap<>();
                ph.put("index", String.valueOf(outpostIndex));
                sendMessage(player, "outpost_spawn_not_set", ph);
                return;
            }
            player.teleport(village.getOutpostSpawn(outpostIndex));
            spawnCooldowns.put(player.getUniqueId(), currentTime);
            Map<String, String> ph = new HashMap<>();
            ph.put("index", String.valueOf(outpostIndex));
            sendMessage(player, "teleported_to_spawn", ph);
        }
    }

    private void handleSetSpawn(Player player, String[] args) {
        String villageName = villageManager.getVillageNameByPlayer(player.getUniqueId());
        if (villageName == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("action", "set its spawnpoint");
            sendMessage(player, "not_in_village", ph);
            return;
        }
        Village village = villageManager.getVillageByName(villageName);
        if (!village.getMayor().equals(player.getUniqueId()) && !village.getCoOwners().contains(player.getUniqueId())) {
            sendMessage(player, "insufficient_rank", null);
            return;
        }
        ChunkCoord currentChunk = new ChunkCoord(player.getLocation().getWorld().getName(),
            player.getLocation().getChunk().getX(),
            player.getLocation().getChunk().getZ());
        if (!villageManager.isChunkClaimed(currentChunk) || 
            !villageManager.getVillageNameByChunk(currentChunk).equalsIgnoreCase(villageName)) {
            sendMessage(player, "not_in_village_territory", null);
            return;
        }
        Integer outpostIndex = null;
        for (Claim c : village.getClaims()) {
            if (c.getChunk().equals(currentChunk) && !c.isMainArea()) {
                outpostIndex = c.getOutpostIndex();
                break;
            }
        }
        // Only set main spawn if not in any outpost
        if (outpostIndex == null) {
            village.setSpawnLocation(player.getLocation());
            sendMessage(player, "main_spawn_set", null);
        } else {
            village.setOutpostSpawn(outpostIndex, player.getLocation());
            Map<String, String> ph = new HashMap<>();
            ph.put("index", String.valueOf(outpostIndex));
            sendMessage(player, "outpost_spawn_set", ph);
        }
    }

    private void handleClaim(Player player, String[] args) {
        String vName = villageManager.getVillageNameByPlayer(player.getUniqueId());
        if (vName == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("action", "claim this chunk");
            sendMessage(player, "not_in_village", ph);
            return;
        }
        Village village = villageManager.getVillageByName(vName);
        if (!village.getMayor().equals(player.getUniqueId()) && !village.getCoOwners().contains(player.getUniqueId())) {
            sendMessage(player, "insufficient_rank", null);
            return;
        }
        Location loc = player.getLocation();
        World world = loc.getWorld();
        int cx = loc.getChunk().getX();
        int cz = loc.getChunk().getZ();
        ChunkCoord chunk = new ChunkCoord(world.getName(), cx, cz);
        if (villageManager.isChunkClaimed(chunk)) {
            sendMessage(player, "chunk_already_claimed", null);
            return;
        }
        if (plugin.getClaimManager().isChunkOverlappingWorldGuardRegion(chunk)) {
            sendMessage(player, "region_protected", null);
            return;
        }
        int minDist = plugin.getConfig().getInt("village.min_distance_between_villages", 8);
        if (plugin.getClaimManager().isChunkNearWorldGuardRegion(chunk, minDist)) {
            Map<String, String> ph = new HashMap<>();
            ph.put("min_distance", String.valueOf(minDist));
            sendMessage(player, "region_too_close_to_protected", ph);
            return;
        }
        if (plugin.getClaimManager().isChunkNearOtherVillage(chunk, village, minDist)) {
            Map<String, String> ph = new HashMap<>();
            ph.put("min_distance", String.valueOf(minDist));
            sendMessage(player, "too_close", ph);
            return;
        }
        ChunkCoord pendingClaim = pendingOutpostClaims.get(player.getUniqueId());
        double claimCost = plugin.getConfig().getDouble("village.claim_cost", 32);
        double creationCost = plugin.getConfig().getDouble("village.creation_cost", 256);
        double outpostClaimCost = plugin.getConfig().getDouble("outpost_claim_cost", 128);
        boolean isFirstClaim = village.getClaims().isEmpty();
        if (pendingClaim != null && pendingClaim.equals(chunk)) {
            if (villageManager.isChunkAdjacentToVillage(chunk, village)) {
                pendingOutpostClaims.remove(player.getUniqueId());
                if (VillagesPlugin.getEconomy().getBalance(player) < claimCost) {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("cost", String.valueOf(claimCost));
                    ph.put("currency", EconomyManager.getName());
                    ph.put("action", "claim this chunk");
                    ph.put("balance", String.valueOf(VillagesPlugin.getEconomy().getBalance(player)));
                    sendMessage(player, "insufficient_funds", ph);
                    return;
                }
                VillagesPlugin.getEconomy().withdrawPlayer(player, claimCost);
                villageManager.claimChunk(village, chunk, null, claimCost);
                String regionId = "village_" + vName.toLowerCase() + "_" + cx + "_" + cz;
                WGUtils.createChunkRegion(world, regionId, cx, cz, null);
                Map<String, String> ph = new HashMap<>();
                ph.put("x", String.valueOf(cx));
                ph.put("z", String.valueOf(cz));
                ph.put("world", world.getName());
                ph.put("cost", String.valueOf(claimCost));
                ph.put("currency", EconomyManager.getName());
                sendMessage(player, "chunk_claimed", ph);
                return;
            }
            if (VillagesPlugin.getEconomy().getBalance(player) < outpostClaimCost) {
                Map<String, String> ph = new HashMap<>();
                ph.put("cost", String.valueOf(outpostClaimCost));
                ph.put("currency", EconomyManager.getName());
                ph.put("action", "create an outpost");
                ph.put("balance", String.valueOf(VillagesPlugin.getEconomy().getBalance(player)));
                sendMessage(player, "insufficient_funds", ph);
                pendingOutpostClaims.remove(player.getUniqueId());
                return;
            }
            VillagesPlugin.getEconomy().withdrawPlayer(player, outpostClaimCost);
            villageManager.claimChunk(village, chunk, null, outpostClaimCost);
            String regionId = "village_" + vName.toLowerCase() + "_" + cx + "_" + cz;
            WGUtils.createChunkRegion(world, regionId, cx, cz, null);
            pendingOutpostClaims.remove(player.getUniqueId());
            Map<String, String> ph = new HashMap<>();
            ph.put("x", String.valueOf(cx));
            ph.put("z", String.valueOf(cz));
            ph.put("world", world.getName());
            ph.put("cost", String.valueOf(outpostClaimCost));
            ph.put("currency", EconomyManager.getName());
            sendMessage(player, "outpost_claimed", ph);
            return;
        }
        if (!villageManager.isChunkAdjacentToVillage(chunk, village)) {
            if (VillagesPlugin.getEconomy().getBalance(player) < outpostClaimCost) {
                Map<String, String> ph = new HashMap<>();
                ph.put("cost", String.valueOf(outpostClaimCost));
                ph.put("currency", EconomyManager.getName());
                ph.put("action", "create an outpost");
                ph.put("balance", String.valueOf(VillagesPlugin.getEconomy().getBalance(player)));
                sendMessage(player, "insufficient_funds", ph);
                return;
            }
            pendingOutpostClaims.put(player.getUniqueId(), chunk);
            Map<String, String> ph = new HashMap<>();
            ph.put("cost", String.valueOf(outpostClaimCost));
            ph.put("currency", EconomyManager.getName());
            sendMessage(player, "outpost_claim", ph);
            return;
        }
        double costToPay = claimCost;
        if (VillagesPlugin.getEconomy().getBalance(player) < costToPay) {
            Map<String, String> ph = new HashMap<>();
            ph.put("cost", String.valueOf(costToPay));
            ph.put("currency", EconomyManager.getName());
            ph.put("action", "claim this chunk");
            ph.put("balance", String.valueOf(VillagesPlugin.getEconomy().getBalance(player)));
            sendMessage(player, "insufficient_funds", ph);
            return;
        }
        VillagesPlugin.getEconomy().withdrawPlayer(player, costToPay);
        boolean claimed = villageManager.claimChunk(village, chunk, null, costToPay);
        if (!claimed) {
            sendMessage(player, "could_not_claim_chunk", null);
            return;
        }
        String regionId = "village_" + vName.toLowerCase() + "_" + cx + "_" + cz;
        WGUtils.createChunkRegion(world, regionId, cx, cz, null);
        Map<String, String> ph = new HashMap<>();
        ph.put("x", String.valueOf(cx));
        ph.put("z", String.valueOf(cz));
        ph.put("world", world.getName());
        ph.put("cost", String.valueOf(costToPay));
        ph.put("currency", EconomyManager.getName());
        sendMessage(player, "chunk_claimed", ph);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private void sendMessage(Player player, String key, Map<String, String> placeholders) {
        String separator = plugin.getConfig().getString("messages.separator", "");
        String title = plugin.getConfig().getString("messages." + key + ".title", "");
        String desc = plugin.getConfig().getString("messages." + key + ".description", "");
        if (separator != null && !separator.isEmpty()) player.sendMessage(separator);
        if (title != null && !title.isEmpty()) player.sendMessage(applyPlaceholders(title, placeholders));
        player.sendMessage(" ");
        if (desc != null && !desc.isEmpty()) {
            for (String line : applyPlaceholders(desc, placeholders).split("\\n")) {
                player.sendMessage(line);
            }
        }
        if (separator != null && !separator.isEmpty()) player.sendMessage(separator);
    }

    private String applyPlaceholders(String msg, Map<String, String> placeholders) {
        if (placeholders == null) return msg;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return msg;
    }

    private String getFlagsListForPlayerChunk(Player player, Village village) {
        org.bukkit.Location loc = player.getLocation();
        org.bukkit.World world = loc.getWorld();
        int cx = loc.getChunk().getX();
        int cz = loc.getChunk().getZ();
        net.doodcraft.cozmyc.villages.models.ChunkCoord chunk = new net.doodcraft.cozmyc.villages.models.ChunkCoord(world.getName(), cx, cz);
        String vName = village.getName();
        if (village.getClaims().stream().noneMatch(c -> c.getChunk().equals(chunk))) {
            // Not in a claimed chunk of this village
            return "  " + getToggleableFlagNames();
        }
        // Get region
        String regionId = "village_" + vName.toLowerCase() + "_" + cx + "_" + cz;
        com.sk89q.worldguard.protection.managers.RegionManager manager = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer().get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
        if (manager == null) return  "  " +  getToggleableFlagNames();
        com.sk89q.worldguard.protection.regions.ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) return  "  " +  getToggleableFlagNames();
        ConfigurationSection toggleableSection = plugin.getConfig().getConfigurationSection("worldguard.mayor_toggleable_flags");
        if (toggleableSection == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String key : toggleableSection.getKeys(false)) {
            if (!toggleableSection.getBoolean(key, false)) continue;
            com.sk89q.worldguard.protection.flags.Flag<?> flag = com.sk89q.worldguard.WorldGuard.getInstance().getFlagRegistry().get(key.replace("-group", ""));
            Object value = null;
            if (key.endsWith("-group")) {
                if (flag != null && flag.getRegionGroupFlag() != null) {
                    value = region.getFlag(flag.getRegionGroupFlag());
                }
            } else if (flag != null) {
                value = region.getFlag(flag);
            }
            sb.append("    - ").append(key).append(": ").append(value != null ? value.toString().toLowerCase() : "unset").append("\n");
        }
        return sb.toString().trim();
    }

    private String getToggleableFlagNames() {
        org.bukkit.configuration.ConfigurationSection toggleableSection = plugin.getConfig().getConfigurationSection("worldguard.mayor_toggleable_flags");
        if (toggleableSection == null) return "";
        List<String> flags = new ArrayList<>();
        for (String key : toggleableSection.getKeys(false)) {
            if (toggleableSection.getBoolean(key, false)) flags.add(key);
        }
        return String.join(", ", flags);
    }

    @Deprecated
    private boolean isInAnyOutpost(ChunkCoord chunk, Village village) {
        for (Claim c : village.getClaims()) {
            if (!c.isMainArea() && c.getChunk().equals(chunk)) return true;
        }
        return false;
    }
}