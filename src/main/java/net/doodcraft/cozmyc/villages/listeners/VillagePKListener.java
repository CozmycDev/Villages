package net.doodcraft.cozmyc.villages.listeners;

import net.doodcraft.cozmyc.villages.VillagesPlugin;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;

public class VillagePKListener implements Listener {
    private final VillagesPlugin plugin;
    public VillagePKListener(VillagesPlugin plugin) {
        this.plugin = plugin;
    }

    public void handleAbilityDamageEntity(Event event) {
        plugin.getPKBridge().handleAbilityDamageEntity(event);
    }

    public void handleBendingReload(Event event) {
        plugin.getPKBridge().handleBendingReload(event);
    }
} 