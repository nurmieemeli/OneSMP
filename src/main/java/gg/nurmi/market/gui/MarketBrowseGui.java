package gg.nurmi.market.gui;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.gui.Pagination;
import gg.nurmi.market.MarketActions;
import gg.nurmi.market.MarketListing;
import gg.nurmi.market.MarketManager;
import gg.nurmi.market.MarketSearchDialog;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class MarketBrowseGui extends AbstractGui {

    public MarketBrowseGui(OneSMPPlugin plugin, MarketManager marketManager, List<MarketListing> listings, String query, int page) {
        super(plugin, title(plugin, query), 6);

        Pagination<MarketListing> pagination = new Pagination<>(listings, PAGE_SIZE);
        List<MarketListing> pageListings = pagination.page(page);

        int slot = 0;
        for (MarketListing listing : pageListings) {
            setButton(slot++, buildIcon(plugin, listing), event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    player.closeInventory();
                    MarketActions.buy(plugin, marketManager, player, listing.id());
                }
            });
        }

        addPaginationFooter(pagination, page, (player, targetPage) ->
                new MarketBrowseGui(plugin, marketManager, listings, query, targetPage).open(player));

        ItemStack searchIcon = new ItemBuilder(Material.COMPASS)
                .name(plugin.messages().text("market.gui-search-button"))
                .lore(plugin.messages().text("market.gui-search-lore"))
                .build();
        setButton(45, searchIcon, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                player.closeInventory();
                MarketSearchDialog.open(plugin, marketManager, player);
            }
        });
    }

    private static Component title(OneSMPPlugin plugin, String query) {
        return query == null || query.isBlank()
                ? plugin.messages().text("market.gui-browse-title")
                : plugin.messages().text("market.gui-browse-title-query",
                        Placeholder.unparsed("query", query));
    }

    private static ItemStack buildIcon(OneSMPPlugin plugin, MarketListing listing) {
        ItemMeta meta = listing.item().getItemMeta();
        List<Component> lore = new ArrayList<>(meta != null && meta.hasLore() ? meta.lore() : List.of());
        lore.add(plugin.messages().text("gui.filler"));
        lore.add(plugin.messages().text("market.gui-seller-lore",
                Placeholder.unparsed("seller", listing.sellerName() == null ? plugin.messages().raw("general.unknown-name") : listing.sellerName())));
        lore.add(plugin.messages().text("market.gui-price-lore",
                Placeholder.unparsed("price", plugin.economy().format(listing.price()))));
        lore.add(plugin.messages().text("market.gui-buy-lore"));

        return new ItemBuilder(listing.item())
                .lore(lore)
                .build();
    }
}
