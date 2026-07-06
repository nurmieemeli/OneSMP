package gg.nurmi.gui;

import org.bukkit.event.inventory.InventoryClickEvent;

@FunctionalInterface
public interface GuiButton {
    void onClick(InventoryClickEvent event);
}
