package gg.nurmi.message;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SocialSpyCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final SocialSpyToggle socialSpy;

    public SocialSpyCommand(CanvasSuitePlugin plugin, SocialSpyToggle socialSpy) {
        this.plugin = plugin;
        this.socialSpy = socialSpy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("canvassuite.msg.socialspy")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }

        boolean nowEnabled = socialSpy.toggle(player.getUniqueId());
        plugin.messages().send(player, nowEnabled ? "msg.socialspy-enabled" : "msg.socialspy-disabled");
        return true;
    }
}
