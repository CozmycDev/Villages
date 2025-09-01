package net.doodcraft.cozmyc.villages.utils;

import org.bukkit.event.Event;

import java.util.Collections;
import java.util.List;

public class PKBridgeNoOp implements PKBridge {
    @Override
    public boolean isEnabled() { return false; }
    @Override
    public List<String> getMainElements() { return Collections.emptyList(); }
    @Override
    public void handleAbilityDamageEntity(Event event) { /* no-op */ }
    @Override
    public void handleBendingReload(Event event) { /* no-op */ }
} 