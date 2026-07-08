package gg.nurmi.packets;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.Bukkit;

public final class PacketEventsBootstrap {

    private final CanvasSuitePlugin plugin;
    private boolean available;

    public PacketEventsBootstrap(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public void detect() {
        this.available = Bukkit.getPluginManager().getPlugin("packetevents") != null;
        if (!available) {
            plugin.getLogger().warning("PacketEvents not found - nametags, moderator vanish, and the "
                    + "reserved-slot tablist will be disabled. Install the PacketEvents plugin to enable them.");
        }
    }

    public boolean available() {
        return available;
    }
}
