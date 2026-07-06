package gg.nurmi.economy;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class EconomyListener implements Listener {

    private final EconomyManager economyManager;

    public EconomyListener(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        economyManager.handleJoin(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        economyManager.handleQuit(event.getPlayer().getUniqueId());
    }
}
