package gg.nurmi.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof AbstractGui gui)) {
            return;
        }

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(top)) {
            if (gui.isOpenSlot(event.getSlot())) {
                gui.onOpenSlotChange(event.getWhoClicked());
                return;
            }
            event.setCancelled(true);
            gui.handleClick(event);
        } else if (event.getClick().isShiftClick()) {
            if (gui.hasOpenSlots()) {
                gui.onOpenSlotChange(event.getWhoClicked());
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof AbstractGui gui)) {
            return;
        }
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize());
        if (!touchesTop) {
            return;
        }
        boolean allOpen = event.getRawSlots().stream()
                .filter(slot -> slot < top.getSize())
                .allMatch(gui::isOpenSlot);
        if (!allOpen) {
            event.setCancelled(true);
            return;
        }
        gui.onOpenSlotChange(event.getWhoClicked());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof AbstractGui gui) {
            gui.dispatchClose(event);
        }
    }
}
