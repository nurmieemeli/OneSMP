package gg.nurmi.maintenance;

import gg.nurmi.OneSMPPlugin;
import net.kyori.adventure.audience.Audience;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class MaintenanceListener implements Listener {

    private final OneSMPPlugin plugin;
    private final MaintenanceManager maintenanceManager;

    public MaintenanceListener(OneSMPPlugin plugin, MaintenanceManager maintenanceManager) {
        this.plugin = plugin;
        this.maintenanceManager = maintenanceManager;
    }

    // Grants bypass holders an exception to Bukkit's own KICK_WHITELIST and swaps in our own kick message, before a Player object even exists.
    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!maintenanceManager.isEnabled()) {
            return;
        }

        if (maintenanceManager.canBypass(event.getUniqueId())) {
            event.allow();
            return;
        }

        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                plugin.messages().render(Audience.empty(), "maintenance.kick-message"));
    }
}
