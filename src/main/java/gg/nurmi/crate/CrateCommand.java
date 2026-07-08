package gg.nurmi.crate;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.util.CommandUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Map;

public final class CrateCommand implements CommandExecutor {

    private static final String USAGE = "/crate <create|remove|key> ...";
    private static final int MAX_TARGET_DISTANCE = 6;

    private final CanvasSuitePlugin plugin;
    private final CrateManager crateManager;

    public CrateCommand(CanvasSuitePlugin plugin, CrateManager crateManager) {
        this.plugin = plugin;
        this.crateManager = crateManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!sender.hasPermission("canvassuite.crate.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.messages().send(sender, "general.unknown-command", Placeholder.unparsed("usage", USAGE));
            return true;
        }

        String action = plugin.subcommandAliases().resolve("crate", args[0]);
        switch (action) {
            case "create" -> handleCreate(sender, args);
            case "remove" -> handleRemove(sender);
            case "key" -> handleKey(sender, args);
            default -> plugin.messages().send(sender, "general.unknown-command", Placeholder.unparsed("usage", USAGE));
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "general.unknown-command", Placeholder.unparsed("usage", "/crate create <type>"));
            return;
        }

        String typeKey = args[1].toLowerCase(Locale.ROOT);
        CrateType type = crateManager.type(typeKey);
        if (type == null) {
            plugin.messages().send(player, "crate.unknown-type", Placeholder.unparsed("type", typeKey));
            return;
        }

        Block target = player.getTargetBlockExact(MAX_TARGET_DISTANCE);
        if (target == null) {
            plugin.messages().send(player, "crate.no-target-block");
            return;
        }

        String existingType = crateManager.crateTypeAt(target.getLocation());
        if (existingType != null) {
            plugin.messages().send(player, "crate.already-bound", Placeholder.unparsed("type", existingType));
            return;
        }

        crateManager.bind(target.getLocation(), typeKey, player.getUniqueId());
        plugin.messages().send(player, "crate.created",
                Placeholder.component("type", plugin.messages().parse(type.displayName())));
    }

    private void handleRemove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return;
        }

        Block target = player.getTargetBlockExact(MAX_TARGET_DISTANCE);
        if (target == null) {
            plugin.messages().send(player, "crate.no-target-block");
            return;
        }
        if (!crateManager.unbind(target.getLocation())) {
            plugin.messages().send(player, "crate.not-a-crate");
            return;
        }
        plugin.messages().send(player, "crate.removed");
    }

    private void handleKey(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "general.unknown-command",
                    Placeholder.unparsed("usage", "/crate key <type> <player> [amount]"));
            return;
        }

        String typeKey = args[1].toLowerCase(Locale.ROOT);
        CrateType type = crateManager.type(typeKey);
        if (type == null) {
            plugin.messages().send(sender, "crate.unknown-type", Placeholder.unparsed("type", typeKey));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "general.player-not-found", Placeholder.unparsed("target", args[2]));
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            Integer parsed = CommandUtil.parseInt(plugin.messages(), sender, args[3]);
            if (parsed == null) {
                return;
            }
            amount = Math.max(1, parsed);
        }

        ItemStack key = crateManager.createKey(typeKey, amount);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(key);
        for (ItemStack overflow : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), overflow);
        }

        plugin.messages().send(sender, "crate.key-given",
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("target", target.getName()),
                Placeholder.component("type", plugin.messages().parse(type.displayName())));
    }
}
