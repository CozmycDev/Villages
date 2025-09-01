package net.doodcraft.cozmyc.villages.commands;

import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.managers.VillageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VillageAdminTabCompleter implements TabCompleter {
    private final VillagesPlugin plugin;
    public VillageAdminTabCompleter(VillagesPlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        VillageManager vm = plugin.getVillageManager();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("delete");
            subs.add("claim");
            subs.add("unclaim");
            subs.add("rename");
            subs.add("info");
            subs.add("reload");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            List<String> names = new ArrayList<>();
            for (String name : vm.getAllVillageNames()) {
                if (!vm.isServerVillage(name)) names.add(name);
            }
            return names.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return Collections.emptyList();
    }
} 