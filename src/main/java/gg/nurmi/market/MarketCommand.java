package gg.nurmi.market;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.market.gui.MarketBrowseGui;
import gg.nurmi.market.gui.MarketMineGui;
import gg.nurmi.util.TextUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class MarketCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("sell", "search", "mine", "cancel");
    private static final String USAGE = "/market [" + String.join("|", SUBCOMMANDS) + " ...]";

    private final OneSMPPlugin plugin;
    private final MarketManager marketManager;

    public MarketCommand(OneSMPPlugin plugin, MarketManager marketManager) {
        this.plugin = plugin;
        this.marketManager = marketManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("onesmp.market.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            handleBrowse(player);
            return true;
        }

        switch (plugin.subcommandAliases().resolve("market", args[0])) {
            case "sell" -> handleSell(player, args);
            case "search" -> handleSearch(player, args);
            case "mine" -> handleMine(player);
            case "cancel" -> handleCancel(player, args);
            default -> plugin.messages().send(player, "general.unknown-command", Placeholder.unparsed("usage", USAGE));
        }
        return true;
    }

    private void handleBrowse(Player player) {
        marketManager.browse(MarketActions.RESULT_LIMIT).thenAccept(listings ->
                plugin.scheduler().runAtEntity(player, () -> {
                    if (listings.isEmpty()) {
                        plugin.messages().send(player, "market.empty");
                        return;
                    }
                    new MarketBrowseGui(plugin, marketManager, listings, null, 0).open(player);
                }, () -> {}));
    }

    private void handleSell(Player player, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(player, "general.unknown-command", Placeholder.unparsed("usage", "/market sell <price>"));
            return;
        }
        long cooldownMillis = plugin.getConfig().getLong("anti-spam.action-cooldown-millis", 500);
        if (!plugin.actionCooldown().tryAcquire(player.getUniqueId(), "market", cooldownMillis)) {
            return;
        }

        BigDecimal price;
        try {
            price = new BigDecimal(args[1]).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException | ArithmeticException ex) {
            plugin.messages().send(player, "general.invalid-number", Placeholder.unparsed("input", args[1]));
            return;
        }
        if (price.signum() <= 0) {
            plugin.messages().send(player, "market.invalid-price");
            return;
        }

        if (player.getInventory().getItemInMainHand().getType() == Material.AIR) {
            plugin.messages().send(player, "market.hand-empty");
            return;
        }

        BigDecimal finalPrice = price;
        int maxListings = Math.max(1, plugin.getConfig().getInt("market.max-listings-per-player", 10));
        marketManager.countListings(player.getUniqueId()).thenAccept(count ->
                plugin.scheduler().runAtEntity(player, () -> {
                    if (count >= maxListings) {
                        plugin.messages().send(player, "market.listing-limit-reached",
                                Placeholder.unparsed("limit", String.valueOf(maxListings)));
                        return;
                    }

                    ItemStack current = player.getInventory().getItemInMainHand();
                    if (current.getType() == Material.AIR) {
                        plugin.messages().send(player, "market.hand-empty");
                        return;
                    }

                    ItemStack listed = current.clone();
                    player.getInventory().setItemInMainHand(null);

                    marketManager.create(player, listed, finalPrice).thenAccept(listing ->
                            plugin.scheduler().runAtEntity(player, () -> plugin.messages().send(player, "market.listed",
                                    Placeholder.unparsed("amount", String.valueOf(listed.getAmount())),
                                    Placeholder.unparsed("item", TextUtil.prettyName(listed.getType())),
                                    Placeholder.unparsed("price", plugin.economy().format(finalPrice))), () -> {})
                    ).exceptionally(ex -> {
                        plugin.scheduler().runAtEntity(player, () -> {
                            player.getInventory().addItem(listed);
                            plugin.messages().send(player, "market.listing-failed");
                        }, () -> {});
                        return null;
                    });
                }, () -> {}));
    }

    // No query typed - show the native search dialog instead of requiring it as a command argument.
    private void handleSearch(Player player, String[] args) {
        if (args.length < 2) {
            MarketSearchDialog.open(plugin, marketManager, player);
            return;
        }
        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        MarketActions.search(plugin, marketManager, player, query);
    }

    private void handleMine(Player player) {
        marketManager.mine(player.getUniqueId()).thenAccept(listings ->
                plugin.scheduler().runAtEntity(player, () -> {
                    if (listings.isEmpty()) {
                        plugin.messages().send(player, "market.no-listings");
                        return;
                    }
                    new MarketMineGui(plugin, marketManager, listings, 0).open(player);
                }, () -> {}));
    }

    private void handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(player, "general.unknown-command", Placeholder.unparsed("usage", "/market cancel <id>"));
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            plugin.messages().send(player, "general.invalid-number", Placeholder.unparsed("input", args[1]));
            return;
        }
        MarketActions.cancel(plugin, marketManager, player, id);
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return plugin.subcommandAliases().labels("market").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
