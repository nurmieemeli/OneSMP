package gg.nurmi.message;

import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class MsgCommand implements CommandExecutor, TabCompleter {

    private final CanvasSuitePlugin plugin;
    private final PrivateMessageManager messageManager;

    public MsgCommand(CanvasSuitePlugin plugin, PrivateMessageManager messageManager) {
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
        if (args.length < 2) {
            plugin.messages().send(player, "general.unknown-command", Placeholder.unparsed("usage", "/msg <player> <message>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.messages().send(player, "general.player-not-found", Placeholder.unparsed("target", args[0]));
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        messageManager.sendMessage(player, target, message);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}
