package gg.nurmi.shop.gui;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.shop.ShopItem;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Map;

public final class SellGui extends AbstractGui {

    private static final int INPUT_SLOTS = 45;
    private static final int INFO_SLOT = 45;
    private static final int CONFIRM_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private final CanvasSuitePlugin plugin;
    private boolean returned;

    public SellGui(CanvasSuitePlugin plugin) {
        super(plugin, plugin.messages().parse("<gradient:#fb923c:#f87171><bold>Sell Items</bold></gradient>"), 6);
        this.plugin = plugin;

        openSlots(0, INPUT_SLOTS);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(plugin.messages().parse(" ")).build();
        for (int slot = INPUT_SLOTS; slot < 54; slot++) {
            if (slot != INFO_SLOT && slot != CONFIRM_SLOT && slot != CLOSE_SLOT) {
                setItem(slot, filler);
            }
        }

        setButton(CONFIRM_SLOT, buildConfirmIcon(), this::handleConfirm);

        ItemStack close = new ItemBuilder(Material.BARRIER).name(plugin.messages().parse("<red>Close")).build();
        setButton(CLOSE_SLOT, close, event -> event.getWhoClicked().closeInventory());

        updateInfo();
    }

    @Override
    protected void onClose(InventoryCloseEvent event) {
        if (returned || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        returned = true;
        returnItems(player);
    }

    @Override
    protected void onOpenSlotChange(HumanEntity viewer) {
        if (viewer instanceof Player player) {
            plugin.scheduler().runAtEntityDelayed(player, this::updateInfo, () -> {}, 1);
        }
    }

    private void handleConfirm(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        BigDecimal total = BigDecimal.ZERO;
        int itemsSold = 0;
        Inventory inventory = getInventory();
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            ShopItem shopItem = plugin.shop().findItem(stack.getType());
            if (shopItem == null || !shopItem.sellable()) {
                continue;
            }
            double effectiveSell = shopItem.sellPrice() * plugin.shop().sellPriceMultiplier();
            total = total.add(BigDecimal.valueOf(effectiveSell).multiply(BigDecimal.valueOf(stack.getAmount())));
            itemsSold += stack.getAmount();
            inventory.setItem(slot, null);
        }

        if (itemsSold == 0) {
            plugin.messages().send(player, "shop.sell-empty");
            return;
        }

        BigDecimal payout = total;
        int soldAmount = itemsSold;
        plugin.economy().deposit(player.getUniqueId(), payout).thenRun(() ->
                plugin.scheduler().runAtEntity(player, () -> {
                    plugin.messages().send(player, "shop.sold-bulk",
                            Placeholder.unparsed("amount", String.valueOf(soldAmount)),
                            Placeholder.unparsed("price", plugin.economy().format(payout)));
                    updateInfo();
                }, () -> {}));
    }

    private void returnItems(Player player) {
        Inventory inventory = getInventory();
        boolean droppedAny = false;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack overflow : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                droppedAny = true;
            }
        }
        if (droppedAny) {
            plugin.messages().send(player, "shop.items-dropped");
        }
    }

    private void updateInfo() {
        BigDecimal total = BigDecimal.ZERO;
        int itemCount = 0;
        Inventory inventory = getInventory();
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            ShopItem shopItem = plugin.shop().findItem(stack.getType());
            if (shopItem == null || !shopItem.sellable()) {
                continue;
            }
            double effectiveSell = shopItem.sellPrice() * plugin.shop().sellPriceMultiplier();
            total = total.add(BigDecimal.valueOf(effectiveSell).multiply(BigDecimal.valueOf(stack.getAmount())));
            itemCount += stack.getAmount();
        }

        setItem(INFO_SLOT, new ItemBuilder(Material.GOLD_INGOT)
                .name(plugin.messages().parse("<yellow>Sellable value: <price>",
                        Placeholder.unparsed("price", plugin.economy().format(total))))
                .lore(plugin.messages().parse("<gray>Sellable items: <white><count>",
                        Placeholder.unparsed("count", String.valueOf(itemCount))))
                .build());
    }

    private ItemStack buildConfirmIcon() {
        return new ItemBuilder(Material.LIME_WOOL)
                .name(plugin.messages().parse("<green><bold>Confirm Sell"))
                .lore(plugin.messages().parse("<gray>Sells everything placed above"))
                .build();
    }
}
