package gg.nurmi.stats;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.util.RecentAttackerTracker;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public final class StatsListener implements Listener {

    private final OneSMPPlugin plugin;
    private final StatsManager statsManager;
    private final RecentAttackerTracker attackerTracker;

    public StatsListener(OneSMPPlugin plugin, StatsManager statsManager, RecentAttackerTracker attackerTracker) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.attackerTracker = attackerTracker;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        statsManager.handleJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        statsManager.handleQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        statsManager.recordDeath(victim.getUniqueId());

        RecentAttackerTracker.KillCredit credit = attackerTracker.resolve(victim);
        if (credit == null) {
            return;
        }
        int streak = statsManager.recordKill(credit.uuid());
        Player killerOnline = Bukkit.getPlayer(credit.uuid());
        if (killerOnline != null) {
            broadcastMilestone(killerOnline, streak);
        }
    }

    private void broadcastMilestone(Player killer, int streak) {
        List<Integer> milestones = plugin.getConfig().getIntegerList("stats.killstreak-broadcast-milestones");
        if (!milestones.contains(streak)) {
            return;
        }
        Bukkit.broadcast(plugin.messages().render(killer, "stats.killstreak-broadcast",
                Placeholder.unparsed("player", killer.getName()),
                Placeholder.unparsed("streak", String.valueOf(streak))));
    }
}
