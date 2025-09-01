package net.doodcraft.cozmyc.villages.utils;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.event.BendingReloadEvent;
import net.doodcraft.cozmyc.villages.VillagesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import java.util.Arrays;
import java.util.List;

public class PKBridgeImpl implements PKBridge {
    private final VillagesPlugin plugin;

    public PKBridgeImpl() {
        this.plugin = VillagesPlugin.getInstance();
    }

    @Override
    public boolean isEnabled() {
        org.bukkit.plugin.Plugin pk = Bukkit.getPluginManager().getPlugin("ProjectKorra");
        return pk != null && pk.isEnabled() && plugin.getConfig().getBoolean("projectkorra.enable_village_element_bonus", true);
    }

    @Override
    public List<String> getMainElements() {
        try {
            Object mainElements = Element.getMainElements();
            if (mainElements instanceof List) {
                List<?> elements = (List<?>) mainElements;
                List<String> names = new java.util.ArrayList<>();
                for (Object e : elements) {
                    names.add(((Element) e).getName().toLowerCase());
                }
                return names;
            } else if (mainElements instanceof Element[]) {
                Element[] elements = (Element[]) mainElements;
                List<String> names = new java.util.ArrayList<>();
                for (Element e : elements) {
                    names.add(e.getName().toLowerCase());
                }
                return names;
            } else if (mainElements instanceof Iterable) {
                List<String> names = new java.util.ArrayList<>();
                for (Object e : (Iterable<?>) mainElements) {
                    names.add(((Element) e).getName().toLowerCase());
                }
                return names;
            }
        } catch (Throwable t) {
            // Fallback to static list
        }
        return Arrays.asList("fire", "earth", "water", "air", "chiblocking");
    }

    private Object getElementByName(String elementName) {
        try {
            return Element.getElement(elementName.substring(0, 1).toUpperCase() + elementName.substring(1).toLowerCase());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void handleAbilityDamageEntity(Event event) {
        // No-op: direct damage buffing is now handled by attribute buffs.
    }

    @Override
    public void handleBendingReload(Event event) {
        if (event instanceof BendingReloadEvent) {
            // nothing for now, later we will use this for attribute buffing
        }
    }
} 