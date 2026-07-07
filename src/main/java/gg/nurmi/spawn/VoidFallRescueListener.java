package gg.nurmi.spawn;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rescues non-creative players who fall out of the void spawn world back to spawn instead of
 * letting them take void damage. Triggers on the actual void damage cause rather than a
 * configured Y threshold, so it lines up exactly with when Minecraft itself would otherwise start
 * hurting the player.
 *
 * <p>{@link VoidWorldListener} already cancels this same damage event for non-creative players in
 * the void world, so this handler deliberately does not use {@code ignoreCancelled}, ensuring it
 * still fires (and rescues) regardless of registration order between the two listeners.</p>
 */
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

        Location spawn = spawnWorldManager.getSpawn();
        player.teleportAsync(spawn).thenAccept(success -> rescuing.remove(player.getUniqueId()));
    }
}
