package gg.nurmi.stats;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.stats.gui.StatsTopGui;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public final class StatsTopCommand implements CommandExecutor {

    private final OneSMPPlugin plugin;
    private final StatsManager statsManager;

    public StatsTopCommand(OneSMPPlugin plugin, StatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("onesmp.stats.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }

        String typeArg = args.length > 0 ? plugin.subcommandAliases().resolve("statstop", args[0]) : "kills";
        StatsManager.StatType type = StatsManager.StatType.fromKey(typeArg).orElse(null);
        if (type == null) {
            plugin.messages().send(sender, "general.unknown-command",
                    Placeholder.unparsed("usage", "/statstop <kills|deaths|killstreak|playtime|kd>"));
            return true;
        }

        statsManager.top(type, 45).thenAccept(entries -> {
            StatsTopGui gui = new StatsTopGui(plugin, type.title(plugin), entries, value -> type.formatValue(plugin, value));
            plugin.scheduler().runAtEntity(player, () -> gui.open(player), () -> {});
        });
        return true;
    }
}
