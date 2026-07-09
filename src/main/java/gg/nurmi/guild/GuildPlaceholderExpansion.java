package gg.nurmi.guild;

import gg.nurmi.OneSMPPlugin;
import io.github.miniplaceholders.api.Expansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.function.Function;

// Named own_name/own_tag, not guild_name/guild_tag - that name is already used elsewhere for an explicitly-queried guild,
// not the viewer's own.
public final class GuildPlaceholderExpansion {

    private GuildPlaceholderExpansion() {
    }

    public static Expansion register(OneSMPPlugin plugin, GuildManager guildManager) {
        Component noGuild = plugin.messages().parse(plugin.getConfig().getString("guild.no-guild-placeholder", "No Guild"));

        Expansion expansion = Expansion.builder("guild")
                .audiencePlaceholder(Player.class, "own_name", (player, queue, ctx) ->
                        Tag.selfClosingInserting(resolve(guildManager, player, Guild::name, noGuild)))
                .audiencePlaceholder(Player.class, "own_tag", (player, queue, ctx) ->
                        Tag.selfClosingInserting(resolve(guildManager, player, Guild::tag, noGuild)))
                .build();
        expansion.register();
        return expansion;
    }

    private static Component resolve(GuildManager guildManager, Player player, Function<Guild, String> field, Component noGuild) {
        Optional<Guild> guild = guildManager.getCachedGuild(player.getUniqueId());
        return guild.<Component>map(g -> Component.text(field.apply(g))).orElse(noGuild);
    }
}
