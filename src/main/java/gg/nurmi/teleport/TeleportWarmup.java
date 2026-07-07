package gg.nurmi.teleport;

import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Handles the "teleporting in Ns, don't move" delay shared by homes/warps/tpa/back. */
public final class TeleportWarmup {

    private final CanvasSuitePlugin plugin;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public TeleportWarmup(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isPending(UUID uuid) {
        return pending.contains(uuid);
    }

    /** Must be called from the player's own entity thread; {@code onComplete} runs on that same thread. */
    public void start(Player player, Runnable onComplete) {
        int warmupSeconds = plugin.getConfig().getInt("teleport.teleport-warmup-seconds", 3);
        if (warmupSeconds <= 0) {
            onComplete.run();
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!pending.add(uuid)) {
            return;
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
            // Player was already retired at the exact moment we tried to schedule - Folia invokes
            // neither callback in that case, so we must clear our own pending state right here or
            // it stays stuck forever, permanently blocking this player's future teleports.
            pending.remove(uuid);
        }
    }

    private boolean hasMoved(Location a, Location b) {
        return a.getWorld() != b.getWorld() || a.distanceSquared(b) > 0.09;
    }
}
