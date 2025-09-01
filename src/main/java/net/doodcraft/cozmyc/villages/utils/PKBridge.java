package net.doodcraft.cozmyc.villages.utils;

import org.bukkit.event.Event;

import java.util.List;

public interface PKBridge {
    boolean isEnabled();
    List<String> getMainElements();
    void handleAbilityDamageEntity(Event event); // No-op: handled by attribute buffs now
    void handleBendingReload(Event event);
} 