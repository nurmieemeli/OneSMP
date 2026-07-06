package gg.nurmi.teleport;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Remembers each player's last location before a plugin teleport (or their death spot) for /back. */
public final class BackManager implements Listener {

    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    /** Must be called from the player's own entity thread (reads live position). */
    public void record(Player player) {
        lastLocations.put(player.getUniqueId(), player.getLocation());
    }

    public Location get(UUID uuid) {
        return lastLocations.get(uuid);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        lastLocations.put(event.getPlayer().getUniqueId(), event.getPlayer().getLocation());
    }
}
