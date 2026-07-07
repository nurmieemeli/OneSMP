package gg.nurmi.moderation;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpectateManager {

    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final PacketVanishController vanishController;

    public SpectateManager(PacketVanishController vanishController) {
        this.vanishController = vanishController;
    }

    public boolean isSpectating(UUID moderator) {
        return active.contains(moderator);
    }

    /** Must already be running on the moderator's own entity thread. */
    public void enter(Player moderator, Location targetLocation) {
        active.add(moderator.getUniqueId());
        moderator.setGameMode(GameMode.SPECTATOR);
        vanishController.hide(moderator);
        moderator.teleportAsync(targetLocation);
    }

    /** Must already be running on the moderator's own entity thread. Always restores Survival gamemode. */
    public void exit(Player moderator, Location spawnLocation) {
        if (!active.remove(moderator.getUniqueId())) {
            return;
        }
        vanishController.show(moderator);
        moderator.setGameMode(GameMode.SURVIVAL);
        moderator.teleportAsync(spawnLocation);
    }
}
