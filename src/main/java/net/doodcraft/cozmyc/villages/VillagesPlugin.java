package net.doodcraft.cozmyc.villages;

import net.doodcraft.cozmyc.villages.commands.VillageAdminCommand;
import net.doodcraft.cozmyc.villages.commands.VillageCommand;
import net.doodcraft.cozmyc.villages.commands.VillageTabCompleter;
import net.doodcraft.cozmyc.villages.listeners.*;
import net.doodcraft.cozmyc.villages.managers.*;
import net.doodcraft.cozmyc.villages.maps.BlueMapIntegration;
import net.doodcraft.cozmyc.villages.maps.XaeroMapIntegration;
import net.doodcraft.cozmyc.villages.models.Village;
import net.doodcraft.cozmyc.villages.utils.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class VillagesPlugin extends JavaPlugin {
    private static Economy econ = null;
    private static VillagesPlugin instance;
    private Plugin worldGuard;
    private Plugin worldEdit;
    private VillageManager villageManager;
    private ClaimManager claimManager;
    private EconomyManager economyManager;
    private PersistenceManager persistenceManager;
    private BukkitTask autoSaveTask;
    private InviteManager inviteManager;
    private InactivityManager inactivityManager;
    private XaeroMapIntegration xaeroMapIntegration;
    private BlueMapIntegration blueMapIntegration;
    private PKBridge pkBridge;
    private BuffManager buffManager;
    private VillageCommand villageCommand;

    @Override
    public void onEnable() {
        instance = this;

        if (getServer().getPluginManager().getPlugin("ProjectKorra") != null) {
            try {
                pkBridge = new PKBridgeImpl();
                getLogger().info("ProjectKorra detected! Using PKBridgeImpl.");
            } catch (Throwable t) {
                pkBridge = new PKBridgeNoOp();
                getLogger().warning("Failed to initialize PKBridgeImpl, using fallback no-op: " + t);
            }
        } else {
            pkBridge = new PKBridgeNoOp();
            getLogger().info("ProjectKorra not detected: using PKBridgeNoOp.");
        }

        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        if (!setupEconomy()) {
            getLogger().severe("Vault not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        EconomyManager.setEconomy(econ);

        villageManager = new VillageManager(this);
        claimManager = new ClaimManager();
        economyManager = new EconomyManager();
        buffManager = new BuffManager(getDataFolder());
        persistenceManager = new PersistenceManager(this, villageManager);

        WGUtils.logAvailableWorldGuardFlags();

        villageManager.loadData();

        this.villageCommand = new VillageCommand(this);
        getCommand("village").setExecutor(this.villageCommand);
        getCommand("village").setTabCompleter(new VillageTabCompleter(this));
        getCommand("villageadmin").setExecutor(new VillageAdminCommand(this));
        getCommand("villageadmin").setTabCompleter(new net.doodcraft.cozmyc.villages.commands.VillageAdminTabCompleter(this));

        getServer().getPluginManager().registerEvents(new ChunkTransitionListener(this), this);
        getServer().getPluginManager().registerEvents(new VillageMonsterListener(villageManager, this), this);
        getServer().getPluginManager().registerEvents(new VillageAnimalListener(villageManager, this), this);
        getServer().getPluginManager().registerEvents(new VillageProtectionListener(this), this);

        getServer().getPluginManager().registerEvents(new VillageListener(this, villageManager), this);

        if (pkBridge.isEnabled()) {
            try {
                PluginManager pm = getServer().getPluginManager();
                VillagePKListener pkListener = new VillagePKListener(this);
                Class<?> abilityEventClass = Class.forName("com.projectkorra.projectkorra.event.AbilityDamageEntityEvent");
                pm.registerEvent(
                    (Class<? extends Event>) abilityEventClass,
                    pkListener,
                    EventPriority.NORMAL,
                    (listener, event) -> ((VillagePKListener) listener).handleAbilityDamageEntity(event),
                    this
                );
                Class<?> reloadEventClass = Class.forName("com.projectkorra.projectkorra.event.BendingReloadEvent");
                pm.registerEvent(
                    (Class<? extends Event>) reloadEventClass,
                    pkListener,
                    EventPriority.NORMAL,
                    (listener, event) -> ((VillagePKListener) listener).handleBendingReload(event),
                    this
                );
                pm.registerEvents(new VillagePKAttributeBuffListener(this, villageManager), this);
                getLogger().info("VillagePKListener and AttributeBuffListener enabled for ProjectKorra integration.");

                pm.registerEvents(new net.doodcraft.cozmyc.villages.listeners.ElementBuffZoneListener(this), this);
            } catch (Exception e) {
                getLogger().warning("Failed to register ProjectKorra event listeners: " + e.getMessage());
            }
        }

        worldGuard = getServer().getPluginManager().getPlugin("WorldGuard");
        if (worldGuard == null) {
            getLogger().severe("WorldGuard not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        worldEdit = getServer().getPluginManager().getPlugin("WorldEdit");
        if (worldEdit == null) {
            getLogger().severe("WorldEdit not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        WGUtils.updateAllRegionDenyMessages();

        inviteManager = new InviteManager(VillagesPlugin.getInstance());

        int interval = getConfig().getInt("village.autosave_minutes", 1) * 60 * 20;
        autoSaveTask = getServer().getScheduler().runTaskTimer(this, () -> {
            villageManager.saveAll();
        }, interval, interval);

        inactivityManager = new InactivityManager(this, villageManager);
        inactivityManager.runCheck();
        getServer().getScheduler().runTaskTimerAsynchronously(
            this,
            inactivityManager::runCheck,
            20L,
            20L * 60 * 60 * 24
        );

        if (Bukkit.getPluginManager().getPlugin("BlueMap") != null) {
            blueMapIntegration = new BlueMapIntegration(this);
            getLogger().info("BlueMap integration enabled.");
        } else {
            blueMapIntegration = null;
            getLogger().info("BlueMap not found, skipping integration.");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new VillagesPlaceholderExpansion(this).register();
        }

        getLogger().info("Villages plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (autoSaveTask != null) autoSaveTask.cancel();
        persistenceManager.saveAll();
        if (villageManager != null) {
            villageManager.saveData();
        }
        
        getLogger().info("VillageNations plugin disabled.");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        var rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }
    public Plugin getWorldGuard() {
        return worldGuard;
    }
    public Plugin getWorldEdit() {
        return worldEdit;
    }

    public VillageManager getVillageManager() {
        return villageManager;
    }
    public ClaimManager getClaimManager() {
        return claimManager;
    }
    public EconomyManager getEconomyManager() {
        return economyManager;
    }
    public PersistenceManager getPersistenceManager() {
        return persistenceManager;
    }
    public InviteManager getInviteManager() {
        return inviteManager;
    }
    public InactivityManager getInactivityManager() { return inactivityManager; }

    public BuffManager getBuffManager() {
        return buffManager;
    }

    public static VillagesPlugin getInstance() {
        return instance;
    }
    
    public BlueMapIntegration getBlueMapIntegration() {
        return blueMapIntegration;
    }

    public PKBridge getPKBridge() { return pkBridge; }

    public VillageCommand getVillageCommand() {
        return villageCommand;
    }

    public void reinitializeAll() {
        for (Village village : villageManager.getAllVillages()) {
            for (org.bukkit.World world : getServer().getWorlds()) {
                WGUtils.updateVillageRegionFlags(village, world);
            }
        }

        WGUtils.updateAllRegionDenyMessages();

        if (blueMapIntegration != null) {
            blueMapIntegration.updateAllClaims();
        }

        getLogger().info("Re-initialized all claims, regions, and map integrations.");
    }
} 