package gg.nurmi.economy;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.util.CommandUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class EcoCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final EconomyManager economyManager;

    public EcoCommand(CanvasSuitePlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("canvassuite.economy.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "general.unknown-command",
                    Placeholder.unparsed("usage", "/eco <give|take|set> <player> <amount>"));
            return true;
        }

        String action = args[0].toLowerCase();
        String targetName = args[1];
        BigDecimal amount = CommandUtil.parseAmount(plugin.messages(), sender, args[2]);
        if (amount == null) {
            return true;
        }

        Player online = Bukkit.getPlayerExact(targetName);
        CompletableFuture<UUID> uuidFuture = online != null
                ? CompletableFuture.completedFuture(online.getUniqueId())
                : economyManager.resolveUuidByName(targetName);

        uuidFuture.thenAccept(uuid -> {
            if (uuid == null) {
                plugin.messages().send(sender, "general.player-not-found", Placeholder.unparsed("target", targetName));
                return;
            }
            switch (action) {
                case "give" -> economyManager.deposit(uuid, amount).thenRun(() ->
                        plugin.messages().send(sender, "economy.eco-give",
                                Placeholder.unparsed("target", targetName),
                                Placeholder.unparsed("amount", economyManager.format(amount))));
                case "take" -> economyManager.withdraw(uuid, amount).thenRun(() ->
                        plugin.messages().send(sender, "economy.eco-take",
                                Placeholder.unparsed("target", targetName),
                                Placeholder.unparsed("amount", economyManager.format(amount))));
                case "set" -> {
                    economyManager.setBalance(uuid, amount);
                    plugin.messages().send(sender, "economy.eco-set",
                            Placeholder.unparsed("target", targetName),
                            Placeholder.unparsed("amount", economyManager.format(amount)));
                }
                default -> plugin.messages().send(sender, "general.unknown-command",
                        Placeholder.unparsed("usage", "/eco <give|take|set> <player> <amount>"));
            }
        });
        return true;
    }
}
