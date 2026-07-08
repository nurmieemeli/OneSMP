package gg.nurmi.stats;

import gg.nurmi.util.TextUtil;
import io.github.miniplaceholders.api.Expansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import org.bukkit.entity.Player;

/**
 * Exposes live stats as MiniPlaceholders tags (e.g. {@code <stats_kills>}), so they can be dropped
 * into any MiniMessage-rendered text - scoreboard lines, tablist, chat, nametags - without those
 * modules needing to know about {@link StatsManager} at all.
 */
public final class StatsPlaceholderExpansion {

    private StatsPlaceholderExpansion() {
    }

    public static Expansion register(StatsManager statsManager) {
        Expansion expansion = Expansion.builder("stats")
                .audiencePlaceholder(Player.class, "kills", (player, queue, ctx) ->
                        Tag.selfClosingInserting(Component.text(snapshot(statsManager, player).kills())))
                .audiencePlaceholder(Player.class, "deaths", (player, queue, ctx) ->
                        Tag.selfClosingInserting(Component.text(snapshot(statsManager, player).deaths())))
                .audiencePlaceholder(Player.class, "kd", (player, queue, ctx) ->
                        Tag.selfClosingInserting(Component.text(formatKd(snapshot(statsManager, player)))))
                .audiencePlaceholder(Player.class, "killstreak", (player, queue, ctx) ->
                        Tag.selfClosingInserting(Component.text(snapshot(statsManager, player).currentKillstreak())))
                .audiencePlaceholder(Player.class, "best_killstreak", (player, queue, ctx) ->
                        Tag.selfClosingInserting(Component.text(snapshot(statsManager, player).bestKillstreak())))
                .audiencePlaceholder(Player.class, "playtime", (player, queue, ctx) ->
                        Tag.selfClosingInserting(Component.text(TextUtil.formatDuration(snapshot(statsManager, player).playtimeSeconds()))))
                .build();
        expansion.register();
        return expansion;
    }

    private static StatsManager.Snapshot snapshot(StatsManager statsManager, Player player) {
        StatsManager.Snapshot snapshot = statsManager.getLiveSnapshot(player.getUniqueId());
        return snapshot != null ? snapshot : StatsManager.EMPTY_SNAPSHOT;
    }

    private static String formatKd(StatsManager.Snapshot snapshot) {
        double kd = snapshot.deaths() == 0 ? snapshot.kills() : (double) snapshot.kills() / snapshot.deaths();
        return String.format("%.2f", kd);
    }
}
