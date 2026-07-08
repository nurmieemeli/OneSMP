package gg.nurmi.teleport;

import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportWarmup {

    private final CanvasSuitePlugin plugin;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public TeleportWarmup(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isPending(UUID uuid) {
        return pending.contains(uuid);
    }

    public void start(Player player, Runnable onComplete) {
        int warmupSeconds = plugin.getConfig().getInt("teleport.teleport-warmup-seconds", 3);
        if (warmupSeconds <= 0) {
            onComplete.run();
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!pending.add(uuid)) {
            return; // already warming up, ignore the duplicate request
        }

        Location start = player.getLocation();
        boolean cancelOnMove = plugin.getConfig().getBoolean("teleport.cancel-warmup-on-move", true);
        plugin.messages().send(player, "teleport.teleporting", Placeholder.unparsed("seconds", String.valueOf(warmupSeconds)));

        boolean scheduled = plugin.scheduler().runAtEntityDelayed(player, () -> {
            pending.remove(uuid);
            if (cancelOnMove && hasMoved(start, player.getLocation())) {
                plugin.messages().send(player, "teleport.teleport-cancelled-move");
                return;
            }
            onComplete.run();
        }, () -> pending.remove(uuid), warmupSeconds * 20L);

        if (!scheduled) {
            pending.remove(uuid);
        }
    }

    private boolean hasMoved(Location a, Location b) {
        return a.getWorld() != b.getWorld() || a.distanceSquared(b) > 0.09;
    }
}
