package gg.nurmi.spawn;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class PlayerRespawnListener implements Listener {

    private final SpawnWorldManager spawnWorldManager;

    public PlayerRespawnListener(SpawnWorldManager spawnWorldManager) {
        this.spawnWorldManager = spawnWorldManager;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        event.setRespawnLocation(spawnWorldManager.getSpawn());
    }
}
