package gg.nurmi.gui;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public abstract class AbstractGui implements InventoryHolder {

    protected final int PAGE_SIZE;
    protected final int footerRowStart;
    private final int prevSlot;
    private final int closeSlot;
    private final int nextSlot;

    private final OneSMPPlugin plugin;
    private final Inventory inventory;
    private final Map<Integer, GuiButton> buttons = new HashMap<>();
    private final Set<Integer> openSlots = new HashSet<>();

    // The last row is always reserved for the footer, so page capacity/footer slots scale with whatever row count a subclass asks for.
    protected AbstractGui(OneSMPPlugin plugin, Component title, int rows) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
        this.footerRowStart = (rows - 1) * 9;
        this.PAGE_SIZE = footerRowStart;
        this.prevSlot = footerRowStart + 3;
        this.closeSlot = footerRowStart + 4;
        this.nextSlot = footerRowStart + 5;
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

    // Bypasses GuiListener's default click/drag cancellation for these slots (e.g. a sell GUI's input area).
    protected void openSlots(int fromInclusive, int toExclusive) {
        for (int slot = fromInclusive; slot < toExclusive; slot++) {
            openSlots.add(slot);
        }
    }

    final boolean isOpenSlot(int slot) {
        return openSlots.contains(slot);
    }

    final boolean hasOpenSlots() {
        return !openSlots.isEmpty();
    }

    // Override to react when the viewer edits an open slot, e.g. refreshing a computed total.
    protected void onOpenSlotChange(HumanEntity viewer) {
    }

    final void handleClick(InventoryClickEvent event) {
        GuiButton button = buttons.get(event.getSlot());
        if (button != null) {
            button.onClick(event);
        }
    }

    protected void onClose(InventoryCloseEvent event) {
    }

    final void dispatchClose(InventoryCloseEvent event) {
        onClose(event);
    }

    public void open(HumanEntity viewer) {
        viewer.openInventory(inventory);
        plugin.effects().guiOpen(viewer);
    }

    protected void addPaginationFooter(Pagination<?> pagination, int page, BiConsumer<Player, Integer> onNavigate) {
        ItemStack close = new ItemBuilder(Material.BARRIER).name(plugin.messages().text("gui.close")).build();
        setButton(closeSlot, close, event -> event.getWhoClicked().closeInventory());

        if (page > 0) {
            ItemStack prev = new ItemBuilder(Material.PAPER).name(plugin.messages().text("gui.previous-page")).build();
            setButton(prevSlot, prev, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    onNavigate.accept(player, page - 1);
                }
            });
        }
        if (page < pagination.pageCount() - 1) {
            ItemStack next = new ItemBuilder(Material.PAPER).name(plugin.messages().text("gui.next-page")).build();
            setButton(nextSlot, next, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    onNavigate.accept(player, page + 1);
                }
            });
        }
    }
}
