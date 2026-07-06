package gg.nurmi.rtp;

import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;

public final class RtpCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final RtpManager rtpManager;

    public RtpCommand(CanvasSuitePlugin plugin, RtpManager rtpManager) {
        this.plugin = plugin;
        this.rtpManager = rtpManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("canvassuite.rtp.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }

        World world = args.length > 0 ? Bukkit.getWorld(args[0]) : player.getWorld();
        if (world == null) {
            plugin.messages().send(player, "general.world-not-found", Placeholder.unparsed("world", args[0]));
            return true;
        }
        if (!rtpManager.isWorldAllowed(world)) {
            plugin.messages().send(player, "rtp.world-disallowed");
            return true;
        }

        boolean bypassCooldown = player.hasPermission("canvassuite.rtp.admin");
        if (!bypassCooldown && rtpManager.isOnCooldown(player.getUniqueId())) {
            plugin.messages().send(player, "rtp.cooldown",
                    Placeholder.unparsed("seconds", String.valueOf(rtpManager.cooldownRemaining(player.getUniqueId()))));
            return true;
        }

        BigDecimal cost = BigDecimal.valueOf(rtpManager.cost());
        if (cost.signum() > 0) {
            plugin.economy().withdraw(player.getUniqueId(), cost).thenAccept(success -> {
                if (!success) {
                    plugin.messages().send(player, "rtp.insufficient-funds",
                            Placeholder.unparsed("price", plugin.economy().format(cost)));
                    return;
                }
                search(player, world);
            });
        } else {
            search(player, world);
        }
        return true;
    }

    private void search(Player player, World world) {
        plugin.messages().send(player, "rtp.searching");
        rtpManager.findSafeLocation(world,
                location -> {
                    rtpManager.applyCooldown(player.getUniqueId());
                    plugin.scheduler().runAtEntity(player, () -> {
                        plugin.backs().record(player);
                        player.teleportAsync(location).thenAccept(success -> {
                            if (success) {
                                plugin.messages().send(player, "rtp.success");
                            }
                        });
                    }, () -> {});
                },
                () -> plugin.messages().send(player, "rtp.failed"));
    }
}
