package gg.nurmi.tablist;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TablistListener implements Listener {

    private final TablistManager manager;

    public TablistListener(TablistManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.refreshHeaderFooter(event.getPlayer());
        manager.onPlayerCountChanged();
        manager.introduceFillersTo(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.onPlayerCountChanged();
    }
}
