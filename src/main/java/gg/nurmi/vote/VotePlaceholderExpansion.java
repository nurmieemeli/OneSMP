package gg.nurmi.vote;

import gg.nurmi.OneSMPPlugin;
import io.github.miniplaceholders.api.Expansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import org.bukkit.entity.Player;

public final class VotePlaceholderExpansion {

    private VotePlaceholderExpansion() {
    }

    // Registers <vote_total>/<vote_streak>/<vote_best_streak> as MiniPlaceholders tags.
    public static Expansion register(OneSMPPlugin plugin, VoteManager voteManager) {
        Expansion expansion = Expansion.builder("vote")
                .audiencePlaceholder(Player.class, "total", (player, queue, ctx) ->
                        Tag.selfClosingInserting(Component.text(snapshot(voteManager, player).totalVotes())))
                .audiencePlaceholder(Player.class, "streak", (player, queue, ctx) ->
                        Tag.selfClosingInserting(Component.text(snapshot(voteManager, player).currentStreak())))
                .audiencePlaceholder(Player.class, "best_streak", (player, queue, ctx) ->
                        Tag.selfClosingInserting(Component.text(snapshot(voteManager, player).bestStreak())))
                .build();
        expansion.register();
        return expansion;
    }

    private static VoteManager.VoteSnapshot snapshot(VoteManager voteManager, Player player) {
        return voteManager.getLiveSnapshot(player.getUniqueId());
    }
}
