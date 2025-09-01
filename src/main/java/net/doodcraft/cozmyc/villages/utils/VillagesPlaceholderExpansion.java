package net.doodcraft.cozmyc.villages.utils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.models.Village;
import org.bukkit.entity.Player;

public class VillagesPlaceholderExpansion extends PlaceholderExpansion {

    private final VillagesPlugin plugin;

    public VillagesPlaceholderExpansion(VillagesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "villages";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) return "";

        if (identifier.equalsIgnoreCase("village_name")) {
            String villageName = plugin.getVillageManager().getVillageNameByPlayer(player.getUniqueId());
            Village village = plugin.getVillageManager().getVillageByName(villageName);
            return village != null ? village.getName().replaceAll("_", " ") : plugin.getConfig().getString("placeholders.no_village", "No Village");
        }

        if (identifier.equalsIgnoreCase("village_element")) {
            if (!plugin.getPKBridge().isEnabled()) return "None";
            String villageName = plugin.getVillageManager().getVillageNameByPlayer(player.getUniqueId());
            Village village = plugin.getVillageManager().getVillageByName(villageName);
            String element = (village != null) ? village.getElement() : null;
            return (element != null && !element.isEmpty()) ? element : plugin.getConfig().getString("placeholders.no_element", "None");
        }

        if (identifier.equalsIgnoreCase("member_rank") || identifier.equalsIgnoreCase("village_member_rank")) {
            String vName = plugin.getVillageManager().getVillageNameByPlayer(player.getUniqueId());
            if (vName == null) {
                return plugin.getConfig().getString("placeholders.rank_none", "None");
            }
            Village village = plugin.getVillageManager().getVillageByName(vName);
            if (village == null) {
                return plugin.getConfig().getString("placeholders.rank_none", "None");
            }
            if (village.getMayor().equals(player.getUniqueId())) {
                return plugin.getConfig().getString("placeholders.rank_mayor", "Mayor");
            }
            if (village.getCoOwners().contains(player.getUniqueId())) {
                return plugin.getConfig().getString("placeholders.rank_coowner", "Co-Owner");
            }
            if (village.getMembers().contains(player.getUniqueId())) {
                return plugin.getConfig().getString("placeholders.rank_member", "Member");
            }
            return plugin.getConfig().getString("placeholders.rank_none", "None");
        }

        return null;
    }
}
