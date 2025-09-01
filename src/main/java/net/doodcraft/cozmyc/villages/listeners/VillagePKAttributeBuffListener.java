package net.doodcraft.cozmyc.villages.listeners;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.attribute.AttributeModification;
import com.projectkorra.projectkorra.attribute.AttributeModifier;
import com.projectkorra.projectkorra.event.AbilityRecalculateAttributeEvent;
import com.projectkorra.projectkorra.event.BendingReloadEvent;
import net.doodcraft.cozmyc.villages.VillagesPlugin;
import net.doodcraft.cozmyc.villages.managers.VillageManager;
import net.doodcraft.cozmyc.villages.models.AttributeBuff;
import net.doodcraft.cozmyc.villages.models.Village;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public class VillagePKAttributeBuffListener implements Listener {
    private final VillagesPlugin plugin;
    private final VillageManager villageManager;
    private final NamespacedKey buffKey;

    public VillagePKAttributeBuffListener(VillagesPlugin plugin, VillageManager villageManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;
        this.buffKey = new NamespacedKey(plugin, "village_buff");
    }

    @EventHandler
    public void onAttributeRecalculate(AbilityRecalculateAttributeEvent event) {
        CoreAbility ability = event.getAbility();
        Player player = ability.getPlayer();
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        String villageName = villageManager.getVillageNameByPlayer(uuid);
        if (villageName == null) {
            //plugin.getLogger().info("[BuffDebug] Player " + player.getName() + " is not in a village.");
            return;
        }
        Village village = villageManager.getVillageByName(villageName);
        if (village == null) {
            //plugin.getLogger().info("[BuffDebug] Village not found for player " + player.getName() + ": " + villageName);
            return;
        }
        int memberCount = village.getMembers() != null ? village.getMembers().size() : 0;
        if (memberCount < 1) {
            //plugin.getLogger().info("[BuffDebug] Village " + village.getName() + " has no members.");
            return;
        }

        Element abilityElement = ability.getElement();
        String abilityMainElement;
        if (abilityElement instanceof Element.SubElement) {
            abilityMainElement = ((Element.SubElement) abilityElement).getParentElement().getName();
        } else {
            abilityMainElement = abilityElement.getName();
        }
        String villageElement = village.getElement();
        // plugin.getLogger().info("[BuffDebug] ability=" + ability.getName() + ", attr=" + event.getAttribute() + ", village=" + village.getName() + ", villageElement=" + villageElement + ", abilityMainElement=" + abilityMainElement + ", memberCount=" + memberCount);
        if (villageElement == null || !villageElement.equalsIgnoreCase(abilityMainElement)) {
            //plugin.getLogger().info("[BuffDebug] Village element does not match ability main element.");
            return;
        }

        String attr = event.getAttribute();
        AttributeBuff buff = plugin.getBuffManager().getBuff(ability.getName(), attr);
        if (buff == null || buff.getType() == AttributeBuff.ScalingType.NONE) {
            return;
        }

        Object originalValue = event.getOriginalValue();
        if (!(originalValue instanceof Number)) {
            return;
        }
        Number base = (Number) originalValue;
        double newValue = base.doubleValue();
        int effectiveMembers = memberCount;
        switch (buff.getType()) {
            case ADDITIVE -> newValue = base.doubleValue() + (buff.getValue() * effectiveMembers);
            case MULTIPLICATIVE -> newValue = base.doubleValue() * (1 + buff.getValue() * effectiveMembers);
            case EXPONENTIAL -> newValue = base.doubleValue() * Math.pow(1 + buff.getValue(), effectiveMembers);
            default -> {}
        }
        Number finalValue;
        if (base instanceof Integer) {
            finalValue = (int) newValue;
        } else if (base instanceof Long) {
            finalValue = (long) newValue;
        } else if (base instanceof Float) {
            finalValue = (float) newValue;
        } else if (base instanceof Short) {
            finalValue = (short) newValue;
        } else if (base instanceof Byte) {
            finalValue = (byte) newValue;
        } else {
            finalValue = newValue;
        }
        if (!finalValue.equals(base)) {
            AttributeModification mod = AttributeModification.of(
                AttributeModifier.SET,
                finalValue,
                AttributeModification.PRIORITY_HIGH,
                buffKey
            );
            event.addModification(mod);
        }
    }

    @EventHandler
    public void onBendingReload(BendingReloadEvent event) {
        plugin.reloadConfig();
        plugin.getBuffManager().loadBuffs();
        plugin.reinitializeAll();
        plugin.getLogger().info("Village config and ability buffs reloaded from config.");
    }
} 