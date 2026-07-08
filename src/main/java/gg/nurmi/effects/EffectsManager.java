package gg.nurmi.effects;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;

/**
 * Purely cosmetic sound/particle feedback layered on top of existing features - never gates
 * behavior, so every method here is safe to call unconditionally from a hot path.
 */
public final class EffectsManager {

    private final CanvasSuitePlugin plugin;

    public EffectsManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled(String path) {
        return plugin.getConfig().getBoolean("effects.enabled", true)
                && plugin.getConfig().getBoolean("effects." + path, true);
    }

    public void success(Player player) {
        if (!enabled("command-feedback")) {
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
    }

    public void failure(Player player) {
        if (!enabled("command-feedback")) {
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
    }

    public void guiOpen(HumanEntity viewer) {
        if (!(viewer instanceof Player player) || !enabled("gui-open")) {
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
    }

    public void teleport(Location location) {
        if (!enabled("teleport")) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        world.spawnParticle(Particle.PORTAL, location.clone().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.5);
    }

    public void join(Player player) {
        if (!enabled("join")) {
            return;
        }
        Location location = player.getLocation();
        player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        World world = location.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, location.clone().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.3);
        }
    }
}
