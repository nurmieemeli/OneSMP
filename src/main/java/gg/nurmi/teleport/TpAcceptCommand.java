package gg.nurmi.teleport;

import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class TpAcceptCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final TpaManager tpaManager;
    private final TeleportExecutor teleportExecutor;

    public TpAcceptCommand(CanvasSuitePlugin plugin, TpaManager tpaManager, TeleportExecutor teleportExecutor) {
        this.plugin = plugin;
        this.tpaManager = tpaManager;
        this.teleportExecutor = teleportExecutor;
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

        TpaManager.TpaRequest request = maybeRequest.get();
        tpaManager.clear(player.getUniqueId());

        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) {
            plugin.messages().send(player, "teleport.tpa-none-pending");
            return true;
        }

        plugin.messages().send(requester, "teleport.tpa-accepted", Placeholder.unparsed("target", player.getName()));

        if (request.here()) {
            teleportExecutor.teleportToPlayerLocation(player, requester, true);
        } else {
            teleportExecutor.teleportToPlayerLocation(requester, player, true);
        }
        return true;
    }
}
