package gg.nurmi.shop.gui;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.shop.ShopCategory;
import gg.nurmi.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ShopCategoriesGui extends AbstractGui {

    public ShopCategoriesGui(CanvasSuitePlugin plugin) {
        super(plugin, plugin.messages().parse("<gradient:#34d399:#10b981><bold>Server Shop</bold></gradient>"), rows(plugin));

        for (ShopCategory category : plugin.shop().categories().values()) {
            ItemStack icon = new ItemBuilder(category.icon())
                    .name(plugin.messages().parse(category.displayName()))
                    .lore(plugin.messages().parse("<gray>Click to browse " + category.items().size() + " items"))
                    .build();
            setButton(category.slot(), icon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new ShopItemsGui(plugin, category, 0).open(player);
                }
            });
        }
    }

    private static int rows(CanvasSuitePlugin plugin) {
        int maxSlot = plugin.shop().categories().values().stream()
                .mapToInt(ShopCategory::slot)
                .max()
                .orElse(0);
        return Math.clamp((maxSlot / 9) + 1, 3, 6);
    }
}
