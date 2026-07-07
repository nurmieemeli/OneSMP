package gg.nurmi.packets;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.Bukkit;

/**
 * PacketEvents is expected to be installed as its own standalone server plugin (soft-depended in
 * plugin.yml, like Vault), not shaded into this jar - so this class never calls
 * PacketEvents.setAPI()/load()/init()/terminate() itself. That lifecycle belongs entirely to the
 * standalone PacketEvents plugin; we only detect whether it's present and expose that to the rest
 * of the plugin, which then calls PacketEvents.getAPI() directly wherever packets are needed.
 */
public final class PacketEventsBootstrap {

    private final CanvasSuitePlugin plugin;
    private boolean available;

    public PacketEventsBootstrap(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    /** Call from CanvasSuitePlugin#onEnable(), after PacketEvents (a softdepend) is guaranteed loaded. */
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
