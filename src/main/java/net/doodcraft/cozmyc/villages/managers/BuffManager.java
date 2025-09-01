package net.doodcraft.cozmyc.villages.managers;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ability.CoreAbility;
import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.models.AttributeBuff;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class BuffManager {
    private final File buffsDir;
    private final Map<String, Map<String, AttributeBuff>> buffs = new HashMap<>(); // abilityName -> attributeName -> buff
    private final Map<String, AttributeBuff> globalBuffs = new HashMap<>(); // attributeName -> buff
    private YamlConfiguration globalBuffConfig;

    public BuffManager(File dataFolder) {
        this.buffsDir = new File(dataFolder, "buffs");
        if (!buffsDir.exists()) buffsDir.mkdirs();
        File globalBuffFile = new File(buffsDir.getParentFile(), "attribute_buffs.yml");
        if (!globalBuffFile.exists()) {
            VillagesPlugin.getInstance().saveResource("attribute_buffs.yml", false);
        }
        loadBuffs();
    }

    public void loadBuffs() {
        buffs.clear();
        globalBuffs.clear();
        File globalBuffFile = new File(buffsDir.getParentFile(), "attribute_buffs.yml");
        if (globalBuffFile.exists()) {
            globalBuffConfig = YamlConfiguration.loadConfiguration(globalBuffFile);
            if (globalBuffConfig.isConfigurationSection("")) {
                for (String attr : globalBuffConfig.getKeys(false)) {
                    ConfigurationSection attrSection = globalBuffConfig.getConfigurationSection(attr);
                    if (attrSection == null) continue;
                    String typeStr = attrSection.getString("type", "NONE").toUpperCase();
                    double value = attrSection.getDouble("value", 0.0);
                    AttributeBuff.ScalingType type;
                    try {
                        type = AttributeBuff.ScalingType.valueOf(typeStr);
                    } catch (Exception e) {
                        type = AttributeBuff.ScalingType.NONE;
                    }
                    globalBuffs.put(attr.toLowerCase(), new AttributeBuff(type, value));
                }
            }
        }
        if (!buffsDir.exists()) return;
        Plugin pk = Bukkit.getPluginManager().getPlugin("ProjectKorra");
        if (pk != null) {
            for (CoreAbility ability : CoreAbility.getAbilities()) {
                if (!ability.isEnabled()) continue;
                String abilityName = ability.getName();
                Element element = ability.getElement();
                String elementName = element.getName();
                File elementDir = new File(buffsDir, elementName);
                if (!elementDir.exists()) elementDir.mkdirs();
                File buffFile = new File(elementDir, abilityName + ".yml");
                if (!buffFile.exists()) {
                    try {
                        YamlConfiguration config = new YamlConfiguration();
                        config.set("displayName", abilityName);
                        config.set("element", elementName);
                        config.set("use_global_buffs", true);
                        // Add all numeric attributes
                        Field[] fields = ability.getClass().getDeclaredFields();
                        for (Field field : fields) {
                            field.setAccessible(true);
                            Object value = null;
                            try { value = field.get(ability); } catch (Exception ignored) {}
                            if (value != null && value instanceof Number) {
                                String attrName = field.getName().toLowerCase();
                                String path = "attributes." + attrName;
                                config.set(path + ".type", "additive");
                                if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
                                    config.set(path + ".value", 0);
                                } else if (value instanceof Float || value instanceof Double) {
                                    config.set(path + ".value", 0.0);
                                } else {
                                    config.set(path + ".value", 0.0); // fallback
                                }
                            }
                        }
                        config.save(buffFile);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        for (File elementDir : buffsDir.listFiles()) {
            if (!elementDir.isDirectory()) continue;
            for (File abilityFile : elementDir.listFiles()) {
                if (!abilityFile.getName().endsWith(".yml")) continue;
                String abilityName = abilityFile.getName().replace(".yml", "");
                YamlConfiguration config = YamlConfiguration.loadConfiguration(abilityFile);
                boolean useGlobalBuffs = config.getBoolean("use_global_buffs", true);
                ConfigurationSection attrs = config.getConfigurationSection("attributes");
                if (attrs == null) continue;
                Map<String, AttributeBuff> attrBuffs = new HashMap<>();
                for (String attr : attrs.getKeys(false)) {
                    ConfigurationSection attrSection = attrs.getConfigurationSection(attr);
                    if (attrSection == null) continue;
                    String typeStr = attrSection.getString("type", "NONE").toUpperCase();
                    double value = attrSection.getDouble("value", 0.0);
                    AttributeBuff.ScalingType type;
                    try {
                        type = AttributeBuff.ScalingType.valueOf(typeStr);
                    } catch (Exception e) {
                        type = AttributeBuff.ScalingType.NONE;
                    }
                    attrBuffs.put(attr.toLowerCase(), new AttributeBuff(type, value));
                }
                // Store useGlobalBuffs as a pseudo-attribute for lookup
                if (useGlobalBuffs) {
                    attrBuffs.put("__use_global_buffs__", new AttributeBuff(AttributeBuff.ScalingType.ADDITIVE, 1));
                } else {
                    attrBuffs.put("__use_global_buffs__", new AttributeBuff(AttributeBuff.ScalingType.NONE, 0));
                }
                buffs.put(abilityName, attrBuffs);
            }
        }
    }

    public AttributeBuff getBuff(String abilityName, String attribute) {
        Map<String, AttributeBuff> attrBuffs = buffs.get(abilityName);
        if (attrBuffs == null) attrBuffs = new HashMap<>();
        boolean useGlobal = attrBuffs.getOrDefault("__use_global_buffs__", new AttributeBuff(AttributeBuff.ScalingType.ADDITIVE, 1)).getType() != AttributeBuff.ScalingType.NONE;
        AttributeBuff global = useGlobal ? globalBuffs.get(attribute.toLowerCase()) : null;
        AttributeBuff local = attrBuffs.get(attribute.toLowerCase());
        if (global == null && local == null) return null;
        if (global == null) return local;
        if (local == null) return global;
        // Combine: apply global first, then individual configs
        return combineBuffs(global, local);
    }

    public Map<String, AttributeBuff> getAllBuffs(String abilityName) {
        Map<String, AttributeBuff> attrBuffs = buffs.getOrDefault(abilityName, new HashMap<>());
        boolean useGlobal = attrBuffs.getOrDefault("__use_global_buffs__", new AttributeBuff(AttributeBuff.ScalingType.ADDITIVE, 1)).getType() != AttributeBuff.ScalingType.NONE;
        Map<String, AttributeBuff> result = new HashMap<>();
        if (useGlobal) {
            for (String attr : globalBuffs.keySet()) {
                result.put(attr, globalBuffs.get(attr));
            }
        }
        for (String attr : attrBuffs.keySet()) {
            if (attr.equals("__use_global_buffs__")) continue;
            AttributeBuff global = useGlobal ? globalBuffs.get(attr) : null;
            AttributeBuff local = attrBuffs.get(attr);
            if (global == null) {
                result.put(attr, local);
            } else if (local == null) {
                result.put(attr, global);
            } else {
                result.put(attr, combineBuffs(global, local));
            }
        }
        return result;
    }

    private AttributeBuff combineBuffs(AttributeBuff global, AttributeBuff local) {
        double base = 1.0;
        double afterGlobal = applyBuff(base, global);
        double afterLocal = applyBuff(afterGlobal, local);
        double netValue;
        switch (local.getType()) {
            case ADDITIVE -> netValue = afterLocal - base;
            case MULTIPLICATIVE -> netValue = (afterLocal / base) - 1.0;
            case EXPONENTIAL -> netValue = Math.pow(afterLocal / base, 1.0);
            default -> netValue = 0.0;
        }
        return new AttributeBuff(local.getType(), netValue);
    }

    private double applyBuff(double base, AttributeBuff buff) {
        return switch (buff.getType()) {
            case ADDITIVE -> base + buff.getValue();
            case MULTIPLICATIVE -> base * (1 + buff.getValue());
            case EXPONENTIAL -> base * Math.pow(1 + buff.getValue(), 1);
            default -> base;
        };
    }
} 