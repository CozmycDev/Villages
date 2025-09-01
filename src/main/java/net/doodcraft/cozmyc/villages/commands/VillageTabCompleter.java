package net.doodcraft.cozmyc.villages.commands;

import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.managers.VillageManager;
import net.doodcraft.cozmyc.villages.models.ChunkCoord;
import net.doodcraft.cozmyc.villages.models.Claim;
import net.doodcraft.cozmyc.villages.models.Village;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VillageTabCompleter implements TabCompleter {
    private final VillagesPlugin plugin;
    private final List<String> allCommands = Arrays.asList(
        "create", "claim", "unclaim", "invite", "accept", "assign", "unassign",
        "promote", "demote", "remove", "leave", "delete", "info", "list",
        "allow", "disallow", "rename", "map", "spawn", "setspawn", "flag"
    );

    public VillageTabCompleter(VillagesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                VillageManager vm = plugin.getVillageManager();
                String vName = vm.getVillageNameByPlayer(player.getUniqueId());
                
                if (vName == null) {
                    // Not in a village, only show create, info, list
                    completions.addAll(Arrays.asList("create", "info", "list", "map"));
                } else {
                    // In a village, show all commands except create
                    completions.addAll(allCommands.stream()
                        .filter(cmd -> !cmd.equals("create"))
                        .collect(Collectors.toList()));
                    // Add 'element' if PK is enabled
                    if (plugin.getPKBridge().isEnabled()) {
                        completions.add("element");
                    }
                }
            } else {
                completions.addAll(Arrays.asList("info", "list"));
            }
            
            // Filter based on partial input
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("map")) {
                List<String> mapCompletions = new ArrayList<>();
                mapCompletions.add("toggle");
                for (int i = 1; i <= 31; i++) {
                    mapCompletions.add(String.valueOf(i));
                }
                return mapCompletions.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sender instanceof Player) {
                Player player = (Player) sender;
                VillageManager vm = plugin.getVillageManager();
                String vName = vm.getVillageNameByPlayer(player.getUniqueId());
                if (vName != null) {
                    Village village = vm.getVillageByName(vName);
                    switch (subCommand) {
                        case "demote":
                            // Suggest co-owner player names (excluding self)
                            for (UUID coowner : village.getCoOwners()) {
                                if (!coowner.equals(player.getUniqueId())) {
                                    Player p = Bukkit.getPlayer(coowner);
                                    if (p != null) completions.add(p.getName());
                                }
                            }
                            break;
                        case "promote":
                            // Suggest member player names (excluding self and already co-owners)
                            for (UUID member : village.getMembers()) {
                                if (!member.equals(player.getUniqueId()) && !village.getCoOwners().contains(member)) {
                                    Player p = Bukkit.getPlayer(member);
                                    if (p != null) completions.add(p.getName());
                                }
                            }
                            break;
                        case "unassign":
                            // Suggest member player names (excluding self)
                            for (UUID member : village.getMembers()) {
                                if (!member.equals(player.getUniqueId())) {
                                    Player p = Bukkit.getPlayer(member);
                                    if (p != null) completions.add(p.getName());
                                }
                            }
                            break;
                        case "invite":
                            // Suggest online players not in a village
                            return Bukkit.getOnlinePlayers().stream()
                                    .filter(p -> !plugin.getVillageManager().isPlayerInVillage(p.getUniqueId()))
                                    .map(Player::getName)
                                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                    .collect(Collectors.toList());
                        case "assign":
                        case "remove":
                        case "spawn":
                            completions.clear();
                            if (village != null && !village.getOutpostIndices().isEmpty()) {
                                for (Integer idx : village.getOutpostIndices()) {
                                    String idxStr = String.valueOf(idx);
                                    if (idxStr.startsWith(args[1])) completions.add(idxStr);
                                }
                            }
                            break;
                        case "setspawn":
                        case "allow":
                        case "disallow":
                            // Suggest village members
                            for (UUID member : village.getMembers()) {
                                if ((subCommand.equals("promote") || subCommand.equals("demote")) && member.equals(player.getUniqueId())) {
                                    continue;
                                }
                                Player p = Bukkit.getPlayer(member);
                                if (p != null) {
                                    completions.add(p.getName());
                                }
                            }
                            break;
                        case "accept":
                            // Suggest numbers 1-9 for pending invites
                            Set<String> pendingInvites = plugin.getInviteManager().getPendingInvites(player.getUniqueId());
                            if (!pendingInvites.isEmpty()) {
                                return IntStream.rangeClosed(1, Math.min(pendingInvites.size(), 9))
                                    .mapToObj(String::valueOf)
                                    .filter(num -> num.startsWith(args[1]))
                                    .collect(Collectors.toList());
                            }
                            break;
                        case "info":
                            // Suggest village names
                            completions.addAll(vm.getAllVillageNames());
                            break;
                        case "element":
                            if (plugin.getPKBridge().isEnabled()) {
                                return plugin.getPKBridge().getMainElements().stream()
                                    .filter(e -> e.startsWith(args[1].toLowerCase()))
                                    .collect(Collectors.toList());
                            }
                            break;
                    }
                }
            }
            // Filter based on partial input
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("create") && plugin.getPKBridge().isEnabled()) {
            return plugin.getPKBridge().getMainElements().stream()
                .filter(e -> e.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 3 && (args[0].equalsIgnoreCase("allow") || args[0].equalsIgnoreCase("disallow")) && sender instanceof Player) {
            Player player = (Player) sender;
            VillageManager vm = plugin.getVillageManager();
            String vName = vm.getVillageNameByPlayer(player.getUniqueId());
            if (vName != null) {
                Village village = vm.getVillageByName(vName);
                org.bukkit.Location loc = player.getLocation();
                org.bukkit.World world = loc.getWorld();
                int cx = loc.getChunk().getX();
                int cz = loc.getChunk().getZ();
                ChunkCoord chunk = new ChunkCoord(world.getName(), cx, cz);
                Claim claim = null;
                for (Claim c : village.getClaims()) {
                    if (c.getChunk().equals(chunk)) {
                        claim = c;
                        break;
                    }
                }
                if (claim != null) {
                    Set<UUID> allowed = claim.getAllowedMembers();
                    for (UUID member : village.getMembers()) {
                        if (args[0].equalsIgnoreCase("allow") && !allowed.contains(member)) {
                            Player p = Bukkit.getPlayer(member);
                            if (p != null) completions.add(p.getName());
                        } else if (args[0].equalsIgnoreCase("disallow") && allowed.contains(member)) {
                            Player p = Bukkit.getPlayer(member);
                            if (p != null) completions.add(p.getName());
                        }
                    }
                }
            }
            completions.removeIf(s -> !s.toLowerCase().startsWith(args[2].toLowerCase()));
            return completions;
        }
        
        // /village flag <flag> <value> [area]
        if (args.length >= 2 && args[0].equalsIgnoreCase("flag")) {
            if (args.length == 2) {
                // Tab complete flag names (toggleable only)
                List<String> flags = new ArrayList<>();
                org.bukkit.configuration.ConfigurationSection toggleableSection = plugin.getConfig().getConfigurationSection("worldguard.mayor_toggleable_flags");
                if (toggleableSection != null) {
                    for (String key : toggleableSection.getKeys(false)) {
                        if (toggleableSection.getBoolean(key, false)) flags.add(key);
                    }
                }
                // Filter out nulls
                return flags.stream().filter(Objects::nonNull).filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (args.length == 3) {
                // Tab complete values for the flag
                String flag = args[1];
                String type = net.doodcraft.cozmyc.villages.utils.WGUtils.getFlagType(flag.replace("-group", ""));
                if (flag.endsWith("-group")) type = "group";
                if (type == null) return Collections.emptyList();
                List<String> values = new ArrayList<>();
                switch (type) {
                    case "group":
                        values.add("none"); values.add("members"); values.add("owners"); values.add("all");
                        break;
                    case "state":
                        values.add("allow"); values.add("deny"); values.add("none");
                        break;
                }
                // Filter out nulls
                return values.stream().filter(Objects::nonNull).filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
            if (args.length == 4) {
                // Tab complete area arg
                List<String> areas = new ArrayList<>();
                areas.add("1x1");
                areas.add("3x3");
                areas.add("4x4");
                // Filter out nulls
                return areas.stream().filter(Objects::nonNull).filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase())).collect(Collectors.toList());
            }
            return Collections.emptyList();
        }
        
        return Collections.emptyList();
    }
} 