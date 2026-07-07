package gg.nurmi.message;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PrivateMessageListener implements Listener {

    private final PrivateMessageManager messageManager;

    public PrivateMessageListener(PrivateMessageManager messageManager) {
        this.messageManager = messageManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        messageManager.handleJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        messageManager.handleQuit(event.getPlayer().getUniqueId());
    }
}
