package gg.nurmi.spawn;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Rescues non-creative players who fall out of the void spawn world back to spawn instead of letting them take void damage. */
public final class VoidFallRescueListener implements Listener {

    private final SpawnWorldManager spawnWorldManager;
    private final Set<UUID> rescuing = ConcurrentHashMap.newKeySet();

    public VoidFallRescueListener(SpawnWorldManager spawnWorldManager) {
        this.spawnWorldManager = spawnWorldManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!spawnWorldManager.isVoidWorld(player.getWorld()) || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (event.getTo().getY() >= spawnWorldManager.voidRescueY()) {
            return;
        }
        if (!rescuing.add(player.getUniqueId())) {
            return;
        }

        Location spawn = spawnWorldManager.getSpawn();
        player.teleportAsync(spawn).thenAccept(success -> rescuing.remove(player.getUniqueId()));
    }
}
