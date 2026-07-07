package gg.nurmi.teleport;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Ties together back-location recording, warmup, and the actual teleportAsync call. */
public final class TeleportExecutor {

    private final CanvasSuitePlugin plugin;
    private final BackManager backManager;
    private final TeleportWarmup warmup;

    public TeleportExecutor(CanvasSuitePlugin plugin, BackManager backManager, TeleportWarmup warmup) {
        this.plugin = plugin;
        this.backManager = backManager;
        this.warmup = warmup;
    }

    /** Must already be running on the player's own entity thread. */
    public void execute(Player player, Location destination, boolean recordBack) {
        if (destination == null) {
            // Home/Warp#toLocation() returns null when the stored world isn't loaded - tell the
            // player instead of silently doing nothing, which looked like the command was ignored.
            plugin.messages().send(player, "teleport.destination-unavailable");
            return;
        }
        if (recordBack) {
            backManager.record(player);
        }
        warmup.start(player, () -> teleportNow(player, destination));
    }

    private void teleportNow(Player player, Location destination) {
        player.teleportAsync(destination).thenAccept(success -> {
            if (success) {
                plugin.messages().send(player, "teleport.teleported");
            }
        });
    }

    /** Safe to call from any thread — hops to the player's entity thread before doing anything. */
    public void executeSafely(Player player, Location destination, boolean recordBack) {
        plugin.scheduler().runAtEntity(player, () -> execute(player, destination, recordBack), () -> {});
    }

    /**
     * Teleports {@code toTeleport} to wherever {@code destinationOwner} currently stands. Reading
     * another player's live location must happen on that player's own entity thread, so this hops
     * there first before handing the (now just plain data) Location off to the destination side.
     */
    public void teleportToPlayerLocation(Player toTeleport, Player destinationOwner, boolean recordBack) {
        plugin.scheduler().runAtEntity(destinationOwner,
                () -> executeSafely(toTeleport, destinationOwner.getLocation(), recordBack), () -> {});
    }
}
