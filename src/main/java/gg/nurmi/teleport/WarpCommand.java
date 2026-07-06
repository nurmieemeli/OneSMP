package gg.nurmi.teleport;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.teleport.gui.WarpsGui;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class WarpCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final WarpManager warpManager;
    private final TeleportExecutor teleportExecutor;

    public WarpCommand(CanvasSuitePlugin plugin, WarpManager warpManager, TeleportExecutor teleportExecutor) {
        this.plugin = plugin;
        this.warpManager = warpManager;
        this.teleportExecutor = teleportExecutor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("canvassuite.warp.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            warpManager.listWarps().thenAccept(warps -> {
                if (warps.isEmpty()) {
                    plugin.messages().send(player, "teleport.no-warps");
                } else {
                    plugin.scheduler().runAtEntity(player, () -> new WarpsGui(plugin, teleportExecutor, warps).open(player), () -> {});
                }
            });
            return true;
        }

        String name = args[0];
        warpManager.getWarp(name).thenAccept(optionalWarp -> {
            if (optionalWarp.isEmpty()) {
                plugin.messages().send(player, "teleport.warp-not-found", Placeholder.unparsed("name", name));
                return;
            }
            teleportExecutor.executeSafely(player, optionalWarp.get().toLocation(), true);
        });
        return true;
    }
}
