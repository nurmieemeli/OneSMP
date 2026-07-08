package gg.nurmi.shop.gui;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.gui.Pagination;
import gg.nurmi.shop.ShopCategory;
import gg.nurmi.shop.ShopItem;
import gg.nurmi.util.ItemBuilder;
import gg.nurmi.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShopItemsGui extends AbstractGui {

    private static final int PAGE_SIZE = 45;
    private static final int BACK_SLOT = 45;
    private static final int PREV_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 50;

    private final CanvasSuitePlugin plugin;

    public ShopItemsGui(CanvasSuitePlugin plugin, ShopCategory category, int page) {
        super(plugin, plugin.messages().parse(category.displayName()), 6);
        this.plugin = plugin;

        Pagination<ShopItem> pagination = new Pagination<>(category.itemList(), PAGE_SIZE);
        List<ShopItem> pageItems = pagination.page(page);

        int slot = 0;
        for (ShopItem item : pageItems) {
            setButton(slot++, buildIcon(item), event -> handleClick(event, item));
        }

        ItemStack back = new ItemBuilder(Material.ARROW).name(plugin.messages().parse("<gray>« Back")).build();
        setButton(BACK_SLOT, back, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                new ShopCategoriesGui(plugin).open(player);
            }
        });

        ItemStack close = new ItemBuilder(Material.BARRIER).name(plugin.messages().parse("<red>Close")).build();
        setButton(CLOSE_SLOT, close, event -> event.getWhoClicked().closeInventory());

        if (page > 0) {
            ItemStack prev = new ItemBuilder(Material.PAPER).name(plugin.messages().parse("<gray>« Previous Page")).build();
            setButton(PREV_SLOT, prev, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new ShopItemsGui(plugin, category, page - 1).open(player);
                }
            });
        }
        if (page < pagination.pageCount() - 1) {
            ItemStack next = new ItemBuilder(Material.PAPER).name(plugin.messages().parse("<gray>Next Page »")).build();
            setButton(NEXT_SLOT, next, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new ShopItemsGui(plugin, category, page + 1).open(player);
                }
            });
        }
    }

    private ItemStack buildIcon(ShopItem item) {
        List<Component> lore = new ArrayList<>();
        if (item.buyable()) {
            lore.add(plugin.messages().parse("<gray>Buy: <green><price></green> <dark_gray>(click)</dark_gray>",
                    Placeholder.unparsed("price", plugin.economy().format(BigDecimal.valueOf(item.buyPrice())))));
        }
        lore.add(plugin.messages().parse("<dark_gray>Shift-click to buy in bulk"));

        return new ItemBuilder(item.material())
                .name(plugin.messages().parse("<white>" + TextUtil.prettyName(item.material())))
                .lore(lore)
                .build();
    }

    private void handleClick(InventoryClickEvent event, ShopItem item) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!item.buyable()) {
            plugin.messages().send(player, "shop.not-buyable");
            return;
        }
        int amount = event.getClick().isShiftClick() ? item.material().getMaxStackSize() : 1;
        handleBuy(player, item, amount);
    }

    private void handleBuy(Player player, ShopItem item, int amount) {
        BigDecimal unitPrice = BigDecimal.valueOf(item.buyPrice());
        BigDecimal totalCost = unitPrice.multiply(BigDecimal.valueOf(amount));

        plugin.economy().withdraw(player.getUniqueId(), totalCost).thenAccept(success ->
                plugin.scheduler().runAtEntity(player, () -> {
                    if (!success) {
                        plugin.messages().send(player, "shop.insufficient-funds",
                                Placeholder.unparsed("price", plugin.economy().format(totalCost)));
                        return;
                    }

                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(item.material(), amount));
                    int notGiven = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                    int given = amount - notGiven;

                    if (notGiven > 0) {
                        BigDecimal refund = unitPrice.multiply(BigDecimal.valueOf(notGiven));
                        plugin.economy().deposit(player.getUniqueId(), refund);
                        plugin.messages().send(player, "shop.inventory-full");
                    }
                    if (given > 0) {
                        plugin.messages().send(player, "shop.bought",
                                Placeholder.unparsed("amount", String.valueOf(given)),
                                Placeholder.unparsed("item", TextUtil.prettyName(item.material())),
                                Placeholder.unparsed("price", plugin.economy().format(unitPrice.multiply(BigDecimal.valueOf(given)))));
                    }
                }, () -> {}));
    }
}