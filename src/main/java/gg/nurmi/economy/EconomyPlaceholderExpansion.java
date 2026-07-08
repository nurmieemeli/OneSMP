package gg.nurmi.economy;

import io.github.miniplaceholders.api.Expansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import org.bukkit.entity.Player;

// Registers <economy_balance> as a MiniPlaceholders tag so any MiniMessage text can use it without depending on EconomyManager.
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
