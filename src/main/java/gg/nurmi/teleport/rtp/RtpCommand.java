package gg.nurmi.teleport.rtp;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.teleport.rtp.gui.RtpWorldSelectGui;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

public final class RtpCommand implements CommandExecutor, TabCompleter {

    private final CanvasSuitePlugin plugin;
    private final RtpManager rtpManager;

    public RtpCommand(CanvasSuitePlugin plugin, RtpManager rtpManager) {
        this.plugin = plugin;
        this.rtpManager = rtpManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("canvassuite.rtp.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            if (rtpManager.enabledWorlds().isEmpty()) {
                plugin.messages().send(player, "rtp.no-worlds-enabled");
                return true;
            }
            new RtpWorldSelectGui(plugin, rtpManager).open(player);
            return true;
        }

        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            plugin.messages().send(player, "general.world-not-found", Placeholder.unparsed("world", args[0]));
            return true;
        }

        rtpManager.teleportRandomly(player, world);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return rtpManager.enabledWorlds().stream()
                .map(World::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
