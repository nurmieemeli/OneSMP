package gg.nurmi.guild;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class GuildCacheListener implements Listener {

    private final GuildManager guildManager;

    public GuildCacheListener(GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        guildManager.handleJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        guildManager.handleQuit(event.getPlayer().getUniqueId());
    }
}
