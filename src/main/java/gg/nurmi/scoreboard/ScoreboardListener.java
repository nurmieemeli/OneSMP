package gg.nurmi.scoreboard;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ScoreboardListener implements Listener {

    private final CanvasSuitePlugin plugin;
    private final ScoreboardManager manager;

    public ScoreboardListener(CanvasSuitePlugin plugin, ScoreboardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.scheduler().runAtEntity(event.getPlayer(), () -> manager.handleJoin(event.getPlayer()), () -> {});
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer());
    }
}
