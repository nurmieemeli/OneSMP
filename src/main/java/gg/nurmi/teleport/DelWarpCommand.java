package gg.nurmi.teleport;

import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class DelWarpCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final WarpManager warpManager;

    public DelWarpCommand(CanvasSuitePlugin plugin, WarpManager warpManager) {
        this.plugin = plugin;
        this.warpManager = warpManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("canvassuite.warp.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.messages().send(sender, "general.unknown-command", Placeholder.unparsed("usage", "/delwarp <name>"));
            return true;
        }

        String name = args[0];
        warpManager.deleteWarp(name).thenAccept(deleted -> {
            if (deleted) {
                plugin.messages().send(sender, "teleport.warp-deleted", Placeholder.unparsed("name", name));
            } else {
                plugin.messages().send(sender, "teleport.warp-not-found", Placeholder.unparsed("name", name));
            }
        });
        return true;
    }
}
