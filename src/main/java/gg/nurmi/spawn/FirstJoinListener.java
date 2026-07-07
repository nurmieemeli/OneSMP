package gg.nurmi.spawn;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class FirstJoinListener implements Listener {

    private final CanvasSuitePlugin plugin;
    private final SpawnWorldManager spawnWorldManager;

    public FirstJoinListener(CanvasSuitePlugin plugin, SpawnWorldManager spawnWorldManager) {
        this.plugin = plugin;
        this.spawnWorldManager = spawnWorldManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) {
            return;
        }
        Location spawn = spawnWorldManager.getSpawn();
        plugin.scheduler().runAtEntity(player, () -> player.teleportAsync(spawn), () -> {});
    }
}
