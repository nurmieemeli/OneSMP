package gg.nurmi.teleport;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BackCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final BackManager backManager;
    private final TeleportExecutor teleportExecutor;

    public BackCommand(CanvasSuitePlugin plugin, BackManager backManager, TeleportExecutor teleportExecutor) {
        this.plugin = plugin;
        this.backManager = backManager;
        this.teleportExecutor = teleportExecutor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("canvassuite.tpa.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }

        Location back = backManager.get(player.getUniqueId());
        if (back == null) {
            plugin.messages().send(player, "teleport.back-none");
            return true;
        }
        teleportExecutor.executeSafely(player, back, false);
        return true;
    }
}
