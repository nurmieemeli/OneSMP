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

public final class PayCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final EconomyManager economyManager;

    public PayCommand(CanvasSuitePlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("canvassuite.economy.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "general.unknown-command", Placeholder.unparsed("usage", "/pay <player> <amount>"));
            return true;
        }

        String targetName = args[0];
        if (targetName.equalsIgnoreCase(player.getName())) {
            plugin.messages().send(player, "economy.pay-self");
            return true;
        }

        BigDecimal amount = CommandUtil.parseAmount(plugin.messages(), sender, args[1]);
        if (amount == null) {
            return true;
        }
        if (amount.signum() <= 0) {
            plugin.messages().send(player, "economy.pay-invalid-amount");
            return true;
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        CompletableFuture<UUID> uuidFuture = onlineTarget != null
                ? CompletableFuture.completedFuture(onlineTarget.getUniqueId())
                : economyManager.resolveUuidByName(targetName);

        uuidFuture.thenAccept(targetUuid -> {
            if (targetUuid == null) {
                plugin.messages().send(player, "general.player-not-found", Placeholder.unparsed("target", targetName));
                return;
            }
            economyManager.withdraw(player.getUniqueId(), amount).thenAccept(success -> {
                if (!success) {
                    plugin.messages().send(player, "economy.pay-insufficient-funds",
                            Placeholder.unparsed("amount", economyManager.format(amount)));
                    return;
                }
                economyManager.deposit(targetUuid, amount).thenRun(() -> {
                    plugin.messages().send(player, "economy.pay-sent",
                            Placeholder.unparsed("target", targetName),
                            Placeholder.unparsed("amount", economyManager.format(amount)));
                    Player targetOnline = Bukkit.getPlayer(targetUuid);
                    if (targetOnline != null) {
                        plugin.messages().send(targetOnline, "economy.pay-received",
                                Placeholder.unparsed("sender", player.getName()),
                                Placeholder.unparsed("amount", economyManager.format(amount)));
                    }
                });
            });
        });
        return true;
    }
}
