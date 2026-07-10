package gg.nurmi.vote;

import gg.nurmi.OneSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.logging.Level;

public final class VoteListener implements Listener {

    private final OneSMPPlugin plugin;
    private final VoteManager voteManager;

    public VoteListener(OneSMPPlugin plugin, VoteManager voteManager) {
        this.plugin = plugin;
        this.voteManager = voteManager;
    }

    // NuVotifier isn't a compile-time dependency, so its event class is looked up via reflection; it registers in Bukkit as "Votifier", not "NuVotifier".
    @SuppressWarnings("unchecked")
    public void registerIfAvailable() {
        if (Bukkit.getPluginManager().getPlugin("Votifier") == null) {
            plugin.getLogger().info("NuVotifier not found - install it for /vote rewards to trigger from real votes.");
            return;
        }
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName("com.vexsoftware.votifier.model.VotifierEvent");
            EventExecutor executor = (listener, event) -> handleVotifierEvent(event);
            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.NORMAL, executor, plugin);
            plugin.getLogger().info("Hooked into NuVotifier for vote rewards.");
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("NuVotifier is installed but its vote event class couldn't be found - vote rewards disabled.");
        }
    }

    private void handleVotifierEvent(Event event) {
        try {
            Method getVote = event.getClass().getMethod("getVote");
            Object vote = getVote.invoke(event);
            Method getUsername = vote.getClass().getMethod("getUsername");
            String username = (String) getUsername.invoke(vote);
            voteManager.handleVote(username);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to read NuVotifier vote payload", ex);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        voteManager.handleJoin(event.getPlayer().getUniqueId());
        voteManager.deliverPending(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        voteManager.handleQuit(event.getPlayer().getUniqueId());
    }
}
