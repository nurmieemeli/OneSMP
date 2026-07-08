package gg.nurmi.spawn;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.world.VoidChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public final class SpawnWorldManager {

    private final CanvasSuitePlugin plugin;

    public SpawnWorldManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public void ensureWorldExists() {
        String worldName = voidWorldName();
        if (Bukkit.getWorld(worldName) != null) {
            return;
        }

        plugin.scheduler().runGlobal(() -> {
            World world = new WorldCreator(worldName)
                    .generator(new VoidChunkGenerator())
                    .environment(World.Environment.NORMAL)
                    .createWorld();
            if (world != null) {
                buildStarterPlatform(world);
            }
        });
    }

    private void buildStarterPlatform(World world) {
        int y = plugin.getConfig().getInt("spawn.y", 65) - 1;

        plugin.scheduler().runAtChunk(world, 0, 0, () -> {
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    world.getBlockAt(x, y, z).setType(Material.STONE);
                }
            }
        });
    }

    public Location getSpawn() {
        String worldName = plugin.getConfig().getString("spawn.world", voidWorldName());
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorld(voidWorldName());
        }
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
            plugin.getLogger().warning("Spawn world '" + worldName + "' isn't loaded yet; "
                    + "falling back to '" + world.getName() + "' until it finishes loading.");
        }
        double x = plugin.getConfig().getDouble("spawn.x", 0.5);
        double y = plugin.getConfig().getDouble("spawn.y", 65.0);
        double z = plugin.getConfig().getDouble("spawn.z", 0.5);
        float yaw = (float) plugin.getConfig().getDouble("spawn.yaw", 0.0);
        float pitch = (float) plugin.getConfig().getDouble("spawn.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    public CompletableFuture<Boolean> teleportToSpawn(Player player) {
        return player.teleportAsync(getSpawn());
    }

    public void setSpawn(Location location) {
        plugin.getConfig().set("spawn.world", location.getWorld().getName());
        plugin.getConfig().set("spawn.x", location.getX());
        plugin.getConfig().set("spawn.y", location.getY());
        plugin.getConfig().set("spawn.z", location.getZ());
        plugin.getConfig().set("spawn.yaw", (double) location.getYaw());
        plugin.getConfig().set("spawn.pitch", (double) location.getPitch());
        plugin.saveConfig();
    }

    public boolean isVoidWorld(World world) {
        return world != null && world.getName().equals(voidWorldName());
    }

    private String voidWorldName() {
        return plugin.getConfig().getString("spawn.world-name", "canvassuite_spawn");
    }
}
