package gg.nurmi.maintenance;

import gg.nurmi.OneSMPPlugin;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

// Thin wrapper around Bukkit's own whitelist: enabling maintenance flips it on, and onesmp.maintenance.bypass lets staff through regardless of whether they're actually whitelisted.
public final class MaintenanceManager {

    private static final String BYPASS_PERMISSION = "onesmp.maintenance.bypass";

    private final OneSMPPlugin plugin;

    public MaintenanceManager(OneSMPPlugin plugin) {
        this.plugin = plugin;
        Bukkit.setWhitelist(isEnabled());
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("maintenance.enabled", false);
    }

    public boolean canBypass(Player player) {
        return player.hasPermission(BYPASS_PERMISSION);
    }

    // Used from AsyncPlayerPreLoginEvent (no Player/Permissible yet); ops always bypass, otherwise this needs LuckPerms to resolve the permission for a UUID that hasn't joined, falling back to op-only without it.
    public boolean canBypass(UUID uniqueId) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uniqueId);
        if (offlinePlayer.isOp()) {
            return true;
        }
        try {
            User user = LuckPermsProvider.get().getUserManager().loadUser(uniqueId).join();
            return user.getCachedData().getPermissionData().checkPermission(BYPASS_PERMISSION).asBoolean();
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    public void enable() {
        plugin.getConfig().set("maintenance.enabled", true);
        plugin.saveConfig();
        Bukkit.setWhitelist(true);
        kickNonBypassing();
    }

    public void disable() {
        plugin.getConfig().set("maintenance.enabled", false);
        plugin.saveConfig();
        Bukkit.setWhitelist(false);
    }

    // Re-applies config.yml's maintenance.enabled to the real whitelist, e.g. after a hand-edit + /onesmp reload.
    public void sync() {
        Bukkit.setWhitelist(isEnabled());
    }

    private void kickNonBypassing() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (canBypass(online)) {
                continue;
            }
            plugin.scheduler().runAtEntity(online,
                    () -> online.kick(plugin.messages().render(online, "maintenance.kick-message")),
                    () -> {});
        }
    }
}
