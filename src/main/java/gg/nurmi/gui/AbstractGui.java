package gg.nurmi.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for every chest-menu GUI in the plugin. Every slot is either empty (click ignored,
 * event still cancelled) or bound to a {@link GuiButton}; there is no free-form item storage, so
 * clicks inside the menu are always cancelled to prevent players pulling/placing items.
 *
 * <p>Callers must open/refresh instances of this class only while already running on the viewing
 * player's own thread (a command handler or an existing {@link InventoryClickEvent} callback both
 * qualify on Folia/Canvas). If you're opening a GUI from an async callback, hop back via
 * {@code player.getScheduler().run(plugin, t -> gui.open(player), null)} first.</p>
 */
public abstract class AbstractGui implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, GuiButton> buttons = new HashMap<>();

    protected AbstractGui(Component title, int rows) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override @NonNull
    public Inventory getInventory() {
        return inventory;
    }

    protected void setButton(int slot, ItemStack item, GuiButton button) {
        inventory.setItem(slot, item);
        buttons.put(slot, button);
    }

    protected void setItem(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        buttons.remove(slot);
    }

    protected void clearSlot(int slot) {
        inventory.setItem(slot, null);
        buttons.remove(slot);
    }

    final void handleClick(InventoryClickEvent event) {
        GuiButton button = buttons.get(event.getSlot());
        if (button != null) {
            button.onClick(event);
        }
    }

    /** Called when the GUI is closed; override to persist state or clean up. */
    protected void onClose(InventoryCloseEvent event) {
    }

    final void dispatchClose(InventoryCloseEvent event) {
        onClose(event);
    }

    public void open(HumanEntity viewer) {
        viewer.openInventory(inventory);
    }
}
