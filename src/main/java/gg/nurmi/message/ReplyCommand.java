package gg.nurmi.message;

import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class ReplyCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final PrivateMessageManager messageManager;

    public ReplyCommand(CanvasSuitePlugin plugin, PrivateMessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("canvassuite.msg.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.messages().send(player, "general.unknown-command", Placeholder.unparsed("usage", "/reply <message>"));
            return true;
        }

        UUID targetUuid = messageManager.lastConversant(player.getUniqueId());
        if (targetUuid == null) {
            plugin.messages().send(player, "msg.no-reply-target");
            return true;
        }
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) {
            plugin.messages().send(player, "msg.reply-target-offline");
            return true;
        }

        String message = String.join(" ", args);
        messageManager.sendMessage(player, target, message);
        return true;
    }
}
