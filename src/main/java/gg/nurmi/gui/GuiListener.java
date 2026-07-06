package gg.nurmi.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Single global listener routing clicks/drags/closes to whichever {@link AbstractGui} is open. */
public final class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof AbstractGui gui)) {
            return;
        }

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(top)) {
            event.setCancelled(true);
            gui.handleClick(event);
        } else if (event.getClick().isShiftClick()) {
            // Block shift-clicking items from the player's own inventory into the menu.
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof AbstractGui && event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof AbstractGui gui) {
            gui.dispatchClose(event);
        }
    }
}
