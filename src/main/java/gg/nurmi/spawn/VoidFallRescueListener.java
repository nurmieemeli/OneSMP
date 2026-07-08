package gg.nurmi.spawn;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoidFallRescueListener implements Listener {

    private final SpawnWorldManager spawnWorldManager;
    private final Set<UUID> rescuing = ConcurrentHashMap.newKeySet();

    public VoidFallRescueListener(SpawnWorldManager spawnWorldManager) {
        this.spawnWorldManager = spawnWorldManager;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!spawnWorldManager.isVoidWorld(player.getWorld()) || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (!rescuing.add(player.getUniqueId())) {
            return;
        }

        spawnWorldManager.teleportToSpawn(player).thenAccept(ignored -> rescuing.remove(player.getUniqueId()));
    }
}
