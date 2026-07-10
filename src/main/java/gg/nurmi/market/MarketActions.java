package gg.nurmi.market;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.market.gui.MarketBrowseGui;
import gg.nurmi.util.TextUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

// Shared buy/cancel/search flows so the /market command, the search dialog, and the browse/mine GUIs stay in sync.
public final class MarketActions {

    public static final int RESULT_LIMIT = 200;

    private MarketActions() {
    }

    public static void search(OneSMPPlugin plugin, MarketManager marketManager, Player player, String query) {
        if (query == null || query.isBlank()) {
            plugin.scheduler().runAtEntity(player, () -> plugin.messages().send(player, "market.search-empty"), () -> {});
            return;
        }
        marketManager.search(query, RESULT_LIMIT).thenAccept(listings ->
                plugin.scheduler().runAtEntity(player, () -> {
                    if (listings.isEmpty()) {
                        plugin.messages().send(player, "market.no-results", Placeholder.unparsed("query", query));
                        return;
                    }
                    new MarketBrowseGui(plugin, marketManager, listings, query, 0).open(player);
                }, () -> {}));
    }

    public static void buy(OneSMPPlugin plugin, MarketManager marketManager, Player buyer, int listingId) {
        long cooldownMillis = plugin.getConfig().getLong("anti-spam.action-cooldown-millis", 500);
        if (!plugin.actionCooldown().tryAcquire(buyer.getUniqueId(), "market", cooldownMillis)) {
            return;
        }
        marketManager.buy(buyer, listingId).thenAccept(outcome ->
                plugin.scheduler().runAtEntity(buyer, () -> {
                    switch (outcome.result()) {
                        case SUCCESS -> {
                            MarketListing listing = outcome.listing();
                            boolean dropped = giveOrDrop(buyer, listing.item());
                            plugin.messages().send(buyer, "market.bought",
                                    Placeholder.unparsed("amount", String.valueOf(listing.item().getAmount())),
                                    Placeholder.unparsed("item", TextUtil.prettyName(listing.item().getType())),
                                    Placeholder.unparsed("seller", listing.sellerName() == null ? plugin.messages().raw("general.unknown-name") : listing.sellerName()),
                                    Placeholder.unparsed("price", plugin.economy().format(listing.price())));
                            if (dropped) {
                                plugin.messages().send(buyer, "market.item-dropped");
                            }
                            notifySeller(plugin, listing, buyer.getName());
                        }
                        case OWN_LISTING -> plugin.messages().send(buyer, "market.own-listing");
                        case INSUFFICIENT_FUNDS -> plugin.messages().send(buyer, "market.insufficient-funds",
                                Placeholder.unparsed("price", plugin.economy().format(outcome.listing().price())));
                        case NOT_FOUND, SOLD_OUT -> plugin.messages().send(buyer, "market.already-sold");
                    }
                }, () -> {}));
    }

    public static void cancel(OneSMPPlugin plugin, MarketManager marketManager, Player player, int listingId) {
        long cooldownMillis = plugin.getConfig().getLong("anti-spam.action-cooldown-millis", 500);
        if (!plugin.actionCooldown().tryAcquire(player.getUniqueId(), "market", cooldownMillis)) {
            return;
        }
        marketManager.cancel(player.getUniqueId(), listingId).thenAccept(outcome ->
                plugin.scheduler().runAtEntity(player, () -> {
                    switch (outcome.result()) {
                        case CANCELLED -> {
                            boolean dropped = giveOrDrop(player, outcome.listing().item());
                            plugin.messages().send(player, "market.cancelled");
                            if (dropped) {
                                plugin.messages().send(player, "market.item-dropped");
                            }
                        }
                        case NOT_OWNER -> plugin.messages().send(player, "general.no-permission");
                        case NOT_FOUND -> plugin.messages().send(player, "market.cancel-not-found");
                    }
                }, () -> {}));
    }

    private static void notifySeller(OneSMPPlugin plugin, MarketListing listing, String buyerName) {
        Player seller = plugin.getServer().getPlayer(listing.sellerUuid());
        if (seller == null) {
            return;
        }
        plugin.scheduler().runAtEntity(seller, () -> plugin.messages().send(seller, "market.sold-notice",
                Placeholder.unparsed("buyer", buyerName),
                Placeholder.unparsed("amount", String.valueOf(listing.item().getAmount())),
                Placeholder.unparsed("item", TextUtil.prettyName(listing.item().getType())),
                Placeholder.unparsed("price", plugin.economy().format(listing.price()))), () -> {});
    }

    private static boolean giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack overflow : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
        return !leftover.isEmpty();
    }
}
