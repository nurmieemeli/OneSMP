package gg.nurmi.stats;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.util.TextUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class StatsCommand implements CommandExecutor {

    private final OneSMPPlugin plugin;
    private final StatsManager statsManager;

    public StatsCommand(OneSMPPlugin plugin, StatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!sender.hasPermission("onesmp.stats.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "general.player-only");
                return true;
            }
            statsManager.getSnapshot(player.getUniqueId()).thenAccept(snapshot ->
                    respond(sender, player.getName(), snapshot, true));
            return true;
        }

        String targetName = args[0];
        Player online = Bukkit.getPlayerExact(targetName);
        CompletableFuture<UUID> uuidFuture = online != null
                ? CompletableFuture.completedFuture(online.getUniqueId())
                : statsManager.resolveUuidByName(targetName);

        uuidFuture.thenAccept(uuid -> {
            if (uuid == null) {
                plugin.messages().send(sender, "general.player-not-found", Placeholder.unparsed("target", targetName));
                return;
            }
            statsManager.getSnapshot(uuid).thenAccept(snapshot -> respond(sender, targetName, snapshot, false));
        });
        return true;
    }

    private void respond(CommandSender sender, String targetName, StatsManager.Snapshot snapshot, boolean self) {
        double kd = snapshot.deaths() == 0 ? snapshot.kills() : (double) snapshot.kills() / snapshot.deaths();
        plugin.messages().send(sender, self ? "stats.self" : "stats.other",
                Placeholder.unparsed("target", targetName),
                Placeholder.unparsed("kills", String.valueOf(snapshot.kills())),
                Placeholder.unparsed("deaths", String.valueOf(snapshot.deaths())),
                Placeholder.unparsed("kd", String.format("%.2f", kd)),
                Placeholder.unparsed("killstreak", String.valueOf(snapshot.currentKillstreak())),
                Placeholder.unparsed("best_killstreak", String.valueOf(snapshot.bestKillstreak())),
                Placeholder.unparsed("playtime", TextUtil.formatDuration(plugin, snapshot.playtimeSeconds())));
    }
}
