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
        execute(player, destination, "teleport.teleported");
    }

    public void execute(Player player, Location destination, String successMessageKey) {
        if (destination == null) {
            plugin.messages().send(player, "teleport.destination-unavailable");
            return;
        }
        warmup.start(player, () -> teleportNow(player, destination, successMessageKey));
    }

    private void teleportNow(Player player, Location destination, String successMessageKey) {
        player.teleportAsync(destination).thenAccept(success -> {
            if (success) {
                plugin.messages().send(player, successMessageKey);
            }
        });
    }

    public void executeSafely(Player player, Location destination) {
        plugin.scheduler().runAtEntity(player, () -> execute(player, destination), () -> {});
    }

    public void executeSafely(Player player, Location destination, String successMessageKey) {
        plugin.scheduler().runAtEntity(player, () -> execute(player, destination, successMessageKey), () -> {});
    }

    public void teleportToPlayerLocation(Player toTeleport, Player destinationOwner) {
        plugin.scheduler().runAtEntity(destinationOwner,
                () -> executeSafely(toTeleport, destinationOwner.getLocation()), () -> {});
    }
}
