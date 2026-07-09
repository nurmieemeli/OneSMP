package gg.nurmi.maintenance;

import gg.nurmi.OneSMPPlugin;
import net.kyori.adventure.audience.Audience;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

// Bukkit already presets KICK_WHITELIST here if the whitelist is on and the UUID isn't allowed; this just grants bypass holders an exception and swaps in our own kick message, before a Player object is even constructed.
public final class MaintenanceListener implements Listener {

    private final OneSMPPlugin plugin;
    private final MaintenanceManager maintenanceManager;

    public MaintenanceListener(OneSMPPlugin plugin, MaintenanceManager maintenanceManager) {
        this.plugin = plugin;
        this.maintenanceManager = maintenanceManager;
    }

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
