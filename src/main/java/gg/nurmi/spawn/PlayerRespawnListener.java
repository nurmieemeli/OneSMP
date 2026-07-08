package gg.nurmi.spawn;

import org.bukkit.block.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class PlayerRespawnListener implements Listener {

    private final SpawnWorldManager spawnWorldManager;

    public PlayerRespawnListener(SpawnWorldManager spawnWorldManager) {
        this.spawnWorldManager = spawnWorldManager;
    }

    @EventHandler
    public void onRespawn(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (player.getBedLocation().getBlock() instanceof Bed
                || player.getBedLocation().getBlock() instanceof RespawnAnchor) {
            return;
        }
        player.setRespawnLocation(spawnWorldManager.getSpawn());
    }
}