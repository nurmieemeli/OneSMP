package gg.nurmi.economy;

import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BalanceCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final EconomyManager economyManager;

    public BalanceCommand(CanvasSuitePlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("canvassuite.economy.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "general.player-only");
                return true;
            }
            economyManager.getBalance(player.getUniqueId()).thenAccept(balance ->
                    plugin.messages().send(player, "economy.balance-self",
                            Placeholder.unparsed("balance", economyManager.format(balance))));
            return true;
        }

        String targetName = args[0];
        Player online = Bukkit.getPlayerExact(targetName);
        CompletableFuture<UUID> uuidFuture = online != null
                ? CompletableFuture.completedFuture(online.getUniqueId())
                : economyManager.resolveUuidByName(targetName);

        uuidFuture.thenAccept(uuid -> {
            if (uuid == null) {
                plugin.messages().send(sender, "general.player-not-found", Placeholder.unparsed("target", targetName));
                return;
            }
            economyManager.getBalance(uuid).thenAccept(balance -> respond(sender, targetName, balance));
        });
        return true;
    }

    private void respond(CommandSender sender, String targetName, BigDecimal balance) {
        plugin.messages().send(sender, "economy.balance-other",
                Placeholder.unparsed("target", targetName),
                Placeholder.unparsed("balance", economyManager.format(balance)));
    }
}
