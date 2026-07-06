package gg.nurmi.teleport;

import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DelHomeCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final HomeManager homeManager;

    public DelHomeCommand(CanvasSuitePlugin plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("canvassuite.home.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.messages().send(sender, "general.unknown-command", Placeholder.unparsed("usage", "/delhome <name>"));
            return true;
        }

        String name = args[0];
        homeManager.deleteHome(player.getUniqueId(), name).thenAccept(deleted -> {
            if (deleted) {
                plugin.messages().send(player, "teleport.home-deleted", Placeholder.unparsed("name", name));
            } else {
                plugin.messages().send(player, "teleport.home-not-found", Placeholder.unparsed("name", name));
            }
        });
        return true;
    }
}
