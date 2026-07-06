package gg.nurmi.economy;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.economy.gui.BalTopGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BalTopCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final EconomyManager economyManager;

    public BalTopCommand(CanvasSuitePlugin plugin, EconomyManager economyManager) {
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

        economyManager.topBalances(45).thenAccept(entries -> {
            BalTopGui gui = new BalTopGui(plugin, entries);
            plugin.scheduler().runAtEntity(player, () -> gui.open(player), () -> {});
        });
        return true;
    }
}
