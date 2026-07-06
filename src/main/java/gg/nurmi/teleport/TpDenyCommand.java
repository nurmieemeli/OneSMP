package gg.nurmi.teleport;

import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class TpDenyCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final TpaManager tpaManager;

    public TpDenyCommand(CanvasSuitePlugin plugin, TpaManager tpaManager) {
        this.plugin = plugin;
        this.tpaManager = tpaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }

        Optional<TpaManager.TpaRequest> maybeRequest = tpaManager.pendingFor(player.getUniqueId());
        if (maybeRequest.isEmpty()) {
            plugin.messages().send(player, "teleport.tpa-none-pending");
            return true;
        }

        tpaManager.clear(player.getUniqueId());
        Player requester = Bukkit.getPlayer(maybeRequest.get().requester());
        if (requester != null) {
            plugin.messages().send(requester, "teleport.tpa-denied", Placeholder.unparsed("target", player.getName()));
        }
        return true;
    }
}
