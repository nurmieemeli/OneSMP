package gg.nurmi.shop;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.shop.gui.SellGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public final class SellCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;

    public SellCommand(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("canvassuite.shop.sell")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        new SellGui(plugin).open(player);
        return true;
    }
}
