package gg.nurmi.teleport;

import gg.nurmi.OneSMPPlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class TeleportExecutor {

    private final OneSMPPlugin plugin;
    private final TeleportWarmup warmup;

    public TeleportExecutor(OneSMPPlugin plugin, TeleportWarmup warmup) {
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
        if (isInCombat(player)) {
            plugin.messages().send(player, "teleport.in-combat");
            return;
        }
        warmup.start(player, () -> teleportNow(player, destination, successMessageKey));
    }

    private boolean isInCombat(Player player) {
        if (!plugin.getConfig().getBoolean("combat-log.disable-teleport", true)) {
            return false;
        }
        long windowMillis = Math.max(0, plugin.getConfig().getInt("combat-log.timeout-seconds", 15)) * 1000L;
        return plugin.attackerTracker().recentAttacker(player.getUniqueId(), windowMillis) != null;
    }

    private void teleportNow(Player player, Location destination, String successMessageKey) {
        Location origin = player.getLocation();
        player.teleportAsync(destination).thenAccept(success -> {
            if (success) {
                // origin may now belong to a different region thread than this callback runs on
                plugin.scheduler().runAtLocation(origin, () -> plugin.effects().teleport(origin));
                plugin.effects().teleport(player.getLocation());
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
        if (isInCombat(destinationOwner)) {
            plugin.messages().send(toTeleport, "teleport.destination-in-combat",
                    Placeholder.unparsed("target", destinationOwner.getName()));
            return;
        }
        plugin.scheduler().runAtEntity(destinationOwner,
                () -> executeSafely(toTeleport, destinationOwner.getLocation()), () -> {});
    }
}
