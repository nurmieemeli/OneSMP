package gg.nurmi.teleport;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class TeleportExecutor {

    private final CanvasSuitePlugin plugin;
    private final TeleportWarmup warmup;

    public TeleportExecutor(CanvasSuitePlugin plugin, TeleportWarmup warmup) {
        this.plugin = plugin;
        this.warmup = warmup;
    }

    public void execute(Player player, Location destination) {
        if (destination == null) {
            plugin.messages().send(player, "teleport.destination-unavailable");
            return;
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

    public void executeSafely(Player player, Location destination) {
        plugin.scheduler().runAtEntity(player, () -> execute(player, destination), () -> {});
    }

    public void teleportToPlayerLocation(Player toTeleport, Player destinationOwner) {
        plugin.scheduler().runAtEntity(destinationOwner,
                () -> executeSafely(toTeleport, destinationOwner.getLocation()), () -> {});
    }
}
