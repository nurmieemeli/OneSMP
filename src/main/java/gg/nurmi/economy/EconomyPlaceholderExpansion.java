package gg.nurmi.economy;

import io.github.miniplaceholders.api.Expansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import org.bukkit.entity.Player;

/**
 * Exposes the cached balance as a MiniPlaceholders tag ({@code <economy_balance>}), so it can be
 * dropped into any MiniMessage-rendered text - scoreboard lines, tablist, chat, nametags - without
 * those modules needing to know about {@link EconomyManager} at all.
 */
public final class EconomyPlaceholderExpansion {

    private EconomyPlaceholderExpansion() {
    }

    public static Expansion register(EconomyManager economyManager) {
        Expansion expansion = Expansion.builder("economy")
                .audiencePlaceholder(Player.class, "balance", (player, queue, ctx) ->
                        Tag.selfClosingInserting(Component.text(economyManager.format(economyManager.getCached(player.getUniqueId())))))
                .build();
        expansion.register();
        return expansion;
    }
}
