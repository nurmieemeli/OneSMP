package gg.nurmi.message.help;

import gg.nurmi.OneSMPPlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

public final class HelpCommand implements CommandExecutor, TabCompleter {

    private final OneSMPPlugin plugin;
    private final HelpManager helpManager;

    public HelpCommand(OneSMPPlugin plugin, HelpManager helpManager) {
        this.plugin = plugin;
        this.helpManager = helpManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("onesmp.help.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            HelpDialogs.openRoot(plugin, helpManager, player);
            return true;
        }

        HelpCategory category = helpManager.category(args[0]);
        if (category == null) {
            plugin.messages().sendRaw(player, helpManager.message("category-not-found"), Placeholder.unparsed("category", args[0]));
            return true;
        }
        if (args.length == 1) {
            HelpDialogs.openCategory(plugin, helpManager, player, category);
            return true;
        }

        HelpArticle article = category.article(args[1]);
        if (article == null) {
            plugin.messages().sendRaw(player, helpManager.message("article-not-found"), Placeholder.unparsed("article", args[1]));
            return true;
        }
        HelpDialogs.openArticle(plugin, helpManager, player, category, article);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return helpManager.categories().stream().map(HelpCategory::key)
                    .filter(key -> key.startsWith(prefix)).toList();
        }
        if (args.length == 2) {
            HelpCategory category = helpManager.category(args[0]);
            if (category == null) {
                return List.of();
            }
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return category.articles().stream().map(HelpArticle::key)
                    .filter(key -> key.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
