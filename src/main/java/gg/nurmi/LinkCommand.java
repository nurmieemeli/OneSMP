package gg.nurmi;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

public final class LinkCommand implements CommandExecutor {

    private final OneSMPPlugin plugin;
    private final String urlConfigPath;
    private final String messageKey;
    private final String permission;

    public LinkCommand(OneSMPPlugin plugin, String urlConfigPath, String messageKey, String permission) {
        this.plugin = plugin;
        this.urlConfigPath = urlConfigPath;
        this.messageKey = messageKey;
        this.permission = permission;
    }

    // Click/hover are built via Adventure's Java API and inserted as a Component - embedding them as MiniMessage tag arguments proved unreliable.
    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!sender.hasPermission(permission)) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }

        String url = plugin.getConfig().getString(urlConfigPath, "");
        if (url.isBlank()) {
            plugin.messages().send(sender, "links.not-configured");
            return true;
        }

        Component urlComponent = Component.text(url)
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text("Click to open", NamedTextColor.GRAY)
                        .appendNewline()
                        .append(Component.text(url, NamedTextColor.WHITE))));
        plugin.messages().send(sender, messageKey, Placeholder.component("url", urlComponent));
        return true;
    }
}