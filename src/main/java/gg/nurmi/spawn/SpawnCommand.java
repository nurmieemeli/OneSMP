package gg.nurmi.spawn;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SpawnCommand implements CommandExecutor {

    private final CanvasSuitePlugin plugin;
    private final SpawnWorldManager spawnWorldManager;

    public SpawnCommand(CanvasSuitePlugin plugin, SpawnWorldManager spawnWorldManager) {
        this.plugin = plugin;
        this.spawnWorldManager = spawnWorldManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("canvassuite.spawn.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }

        Location spawn = spawnWorldManager.getSpawn();
        plugin.scheduler().runAtEntity(player, () -> player.teleportAsync(spawn).thenAccept(success -> {
            if (success) {
                plugin.messages().send(player, "spawn.teleported");
            }
        }), () -> {});
        return true;
    }
}
