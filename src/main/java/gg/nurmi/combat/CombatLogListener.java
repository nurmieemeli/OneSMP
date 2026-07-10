package gg.nurmi.combat;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.util.RecentAttackerTracker;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CombatLogListener implements Listener {

    private final OneSMPPlugin plugin;
    private final RecentAttackerTracker attackerTracker;

    public CombatLogListener(OneSMPPlugin plugin, RecentAttackerTracker attackerTracker) {
        this.plugin = plugin;
        this.attackerTracker = attackerTracker;
    }

    // Kills a player who quits shortly after being hit, via Player#damage() so totems/absorption/resistance still apply like any other lethal hit.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("combat-log.enabled", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isDead() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        long windowMillis = Math.max(0, plugin.getConfig().getInt("combat-log.timeout-seconds", 15)) * 1000L;
        if (attackerTracker.recentAttacker(player.getUniqueId(), windowMillis) == null) {
            return;
        }
        player.damage(1000.0);
    }
}
