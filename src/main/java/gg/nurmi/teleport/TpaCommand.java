package gg.nurmi.teleport;

import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TpaCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final TpaManager tpaManager;
    private final boolean here;

    public TpaCommand(CanvasSuitePlugin plugin, TpaManager tpaManager, boolean here) {
        this.plugin = plugin;
        this.tpaManager = tpaManager;
        this.here = here;
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
        if (args.length == 0) {
            plugin.messages().send(sender, "general.unknown-command",
                    Placeholder.unparsed("usage", "/" + label + " <player>"));
            return true;
        }

        String targetName = args[0];
        if (targetName.equalsIgnoreCase(player.getName())) {
            plugin.messages().send(player, "teleport.tpa-self");
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            plugin.messages().send(player, "general.player-not-found", Placeholder.unparsed("target", targetName));
            return true;
        }

        if (tpaManager.hasPendingTo(player.getUniqueId(), target.getUniqueId())) {
            plugin.messages().send(player, "teleport.tpa-already-pending", Placeholder.unparsed("target", target.getName()));
            return true;
        }

        tpaManager.createRequest(player.getUniqueId(), target.getUniqueId(), here);
        plugin.messages().send(player, "teleport.tpa-sent", Placeholder.unparsed("target", target.getName()));
        plugin.messages().send(target, here ? "teleport.tpahere-received" : "teleport.tpa-received",
                Placeholder.unparsed("sender", player.getName()));
        return true;
    }
}
