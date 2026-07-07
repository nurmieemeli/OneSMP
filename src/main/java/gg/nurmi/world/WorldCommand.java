package gg.nurmi.world;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.world.gui.WorldCreationGui;
import gg.nurmi.world.gui.WorldListGui;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class WorldCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("create", "list", "delete", "tp");

    private final CanvasSuitePlugin plugin;
    private final WorldManager worldManager;

    public WorldCommand(CanvasSuitePlugin plugin, WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("canvassuite.world.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.messages().send(player, "general.unknown-command",
                    Placeholder.unparsed("usage", "/world <" + String.join("|", SUBCOMMANDS) + ">"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> handleCreate(player, args);
            case "list" -> new WorldListGui(plugin, worldManager, 0).open(player);
            case "delete" -> handleDelete(player, args);
            case "tp" -> handleTeleport(player, args);
            default -> plugin.messages().send(player, "general.unknown-command",
                    Placeholder.unparsed("usage", "/world <" + String.join("|", SUBCOMMANDS) + ">"));
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(player, "general.unknown-command", Placeholder.unparsed("usage", "/world create <name> [seed]"));
            return;
        }
        String name = args[1];
        if (!worldManager.isValidName(name)) {
            plugin.messages().send(player, "world.invalid-name");
            return;
        }
        if (worldManager.worldNameTaken(name)) {
            plugin.messages().send(player, "world.name-taken", Placeholder.unparsed("name", name));
            return;
        }

        Long fixedSeed = null;
        if (args.length >= 3) {
            try {
                fixedSeed = Long.parseLong(args[2]);
            } catch (NumberFormatException ex) {
                fixedSeed = (long) args[2].hashCode();
            }
        }

        worldManager.startSession(player.getUniqueId(), name, fixedSeed);
        new WorldCreationGui(plugin, worldManager, player.getUniqueId()).open(player);
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(player, "general.unknown-command", Placeholder.unparsed("usage", "/world delete <name> [wipe]"));
            return;
        }
        String name = args[1];
        if (worldManager.getSettings(name) == null) {
            plugin.messages().send(player, "world.not-found", Placeholder.unparsed("name", name));
            return;
        }
        boolean wipe = args.length >= 3 && args[2].equalsIgnoreCase("wipe");
        worldManager.deleteWorld(name, wipe, success -> {
            if (success) {
                plugin.messages().send(player, wipe ? "world.wiped" : "world.deleted", Placeholder.unparsed("name", name));
            }
        });
    }

    private void handleTeleport(Player player, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(player, "general.unknown-command", Placeholder.unparsed("usage", "/world tp <name>"));
            return;
        }
        String name = args[1];
        World world = Bukkit.getWorld(name);
        if (world == null || worldManager.getSettings(name) == null) {
            plugin.messages().send(player, "world.not-found", Placeholder.unparsed("name", name));
            return;
        }
        // TeleportExecutor sends its own "teleported" confirmation once the (possibly warmed-up) teleport actually completes.
        plugin.teleportExecutor().executeSafely(player, world.getSpawnLocation(), true);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && List.of("delete", "tp").contains(args[0].toLowerCase(Locale.ROOT))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return worldManager.listWorlds().stream().map(WorldSettings::name)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}
