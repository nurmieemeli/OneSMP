package gg.nurmi.guild;

import gg.nurmi.CanvasSuitePlugin;
import io.github.miniplaceholders.api.Expansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.function.Function;

/**
 * Exposes the viewer's own cached guild name/tag as MiniPlaceholders tags
 * ({@code <guild_own_name>}, {@code <guild_own_tag>}), so they can be dropped into any
 * MiniMessage-rendered text - scoreboard lines, tablist, nametags - without those modules needing
 * to know about {@link GuildManager} at all.
 *
 * <p>Deliberately NOT named {@code <guild_name>}/{@code <guild_tag>}: those names are already used
 * throughout this codebase (chat formatting, {@code /guild info}, {@code /guild list}, the guild
 * GUIs) as explicit per-call placeholders that can refer to a queried/other guild, not necessarily
 * the viewer's own. Registering a global tag under the same name would silently shadow those
 * (global placeholders resolve before per-call ones - see {@code MessageService#render}), so a
 * distinct name is used here instead.</p>
 *
 * <p>For players not in a guild, resolves to the MiniMessage-parsed
 * {@code guild.no-guild-placeholder} config string (default {@code "No Guild"}) instead of an
 * empty string, so scoreboard/tablist lines built around these tags don't look broken.</p>
 */
public final class GuildPlaceholderExpansion {

    private GuildPlaceholderExpansion() {
    }

    public static Expansion register(CanvasSuitePlugin plugin, GuildManager guildManager) {
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
