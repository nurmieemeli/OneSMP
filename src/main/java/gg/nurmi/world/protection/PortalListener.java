package gg.nurmi.world.protection;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.PortalCreateEvent;

public final class PortalListener implements Listener {

    private final CanvasSuitePlugin plugin;

    public PortalListener(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (!plugin.getConfig().getBoolean("protection.disable-nether-portal-creation", true)) {
            return;
        }
        if (event.getReason() == PortalCreateEvent.CreateReason.FIRE
                || event.getReason() == PortalCreateEvent.CreateReason.NETHER_PAIR) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Player player) {
                plugin.messages().send(player, "protection.portal-blocked");
            }
        }
    }
}
