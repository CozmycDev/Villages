package net.doodcraft.cozmyc.villages.managers;

import net.doodcraft.cozmyc.villages.VillagesPlugin;

public class PersistenceManager {
    private final VillagesPlugin plugin;
    private final VillageManager villageManager;

    public PersistenceManager(VillagesPlugin plugin, VillageManager vm) {
        this.plugin = plugin;
        this.villageManager = vm;
    }
    public void saveAll() {
        villageManager.saveAll();
    }
    public void loadAll() {
        villageManager.loadAll();
    }
    public void startAutoSave(long intervalTicks) {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveAll, intervalTicks, intervalTicks);
    }
} 