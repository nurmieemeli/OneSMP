package gg.nurmi.chat;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.guild.Guild;
import gg.nurmi.guild.GuildMember;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Map;

/**
 * Renders every chat message through MiniMessage + MiniPlaceholders. Untrusted player-typed text
 * is inserted as a plain Component (never reparsed as MiniMessage) unless the sender holds
 * canvassuite.chat.format, so players can't inject <click>/<hover>/color tags into chat.
 */
public final class ChatFormatListener implements Listener {

    private final CanvasSuitePlugin plugin;

    public ChatFormatListener(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();

        if (plugin.guildChat().isToggled(sender.getUniqueId())) {
            handleGuildChat(event, sender);
            return;
        }

        Component messageComponent = renderMessageContent(event, sender);
        String format = resolveFormat(sender);

        // AsyncChatEvent runs off the region thread specifically so plugins can do blocking work
        // like this DB lookup; safe here in a way it would not be on a region/entity thread.
        Guild guild = plugin.guilds().getGuildByMember(sender.getUniqueId()).join().orElse(null);
        Component guildSegment = buildGuildSegment(sender, guild);

        event.renderer((source, sourceDisplayName, message, viewer) ->
                plugin.messages().renderRelationalRaw(source, viewer, format,
                        Placeholder.component("message", messageComponent),
                        Placeholder.component("guild_segment", guildSegment)));
    }

    private Component buildGuildSegment(Player sender, Guild guild) {
        if (guild == null) {
            return Component.empty();
        }
        String segmentFormat = plugin.getConfig().getString("chat.guild-segment-format", "<gray>[<guild_tag>]</gray> ");
        return plugin.messages().parse(segmentFormat, sender, Placeholder.unparsed("guild_tag", guild.tag()));
    }

    private void handleGuildChat(AsyncChatEvent event, Player sender) {
        event.setCancelled(true);
        Component messageComponent = renderMessageContent(event, sender);

        plugin.guilds().getGuildByMember(sender.getUniqueId()).thenAccept(optionalGuild -> {
            if (optionalGuild.isEmpty()) {
                plugin.guildChat().clear(sender.getUniqueId());
                plugin.messages().send(sender, "guild.not-in-guild");
                return;
            }

            Guild guild = optionalGuild.get();
            String format = plugin.getConfig().getString("chat.guild-chat-format", "<aqua>[G] <player_name>: <message>");

            for (GuildMember member : guild.members()) {
                Player online = Bukkit.getPlayer(member.uuid());
                if (online == null) {
                    continue;
                }
                Component rendered = plugin.messages().renderRelationalRaw(sender, online, format,
                        Placeholder.component("message", messageComponent),
                        Placeholder.unparsed("guild_name", guild.name()),
                        Placeholder.unparsed("guild_tag", guild.tag()));
                online.sendMessage(rendered);
            }
        });
    }

    private Component renderMessageContent(AsyncChatEvent event, Player sender) {
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        return sender.hasPermission("canvassuite.chat.format")
                ? plugin.messages().parse(plainMessage)
                : Component.text(plainMessage);
    }

    private String resolveFormat(Player sender) {
        List<?> formats = plugin.getConfig().getMapList("chat.formats");
        for (Object entry : formats) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            String permission = String.valueOf(map.get("permission"));
            String format = String.valueOf(map.get("format"));
            if (permission.isBlank() || sender.hasPermission(permission)) {
                return format;
            }
        }
        return "<white><player_name></white><dark_gray>:</dark_gray> <gray><message>";
    }
}
