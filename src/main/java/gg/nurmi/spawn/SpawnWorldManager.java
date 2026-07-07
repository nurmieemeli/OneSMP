package gg.nurmi.spawn;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.world.VoidChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * Owns the single void spawn world: creates it once on first enable (with a small starter
 * platform so /spawn and first-join don't immediately drop players into the void-rescue loop),
 * and reads/writes the single global spawn point in config.yml.
 */
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

        // World creation is a one-time, global operation — not tied to any specific region.
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
        Material material = Material.matchMaterial(plugin.getConfig().getString("spawn.platform-material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }
        int y = plugin.getConfig().getInt("spawn.y", 65) - 1;
        Material platformMaterial = material;

        // The 3x3 platform (x/z in 0..2) stays within a single chunk, so one runAtChunk call is safe.
        plugin.scheduler().runAtChunk(world, 0, 0, () -> {
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    world.getBlockAt(x, y, z).setType(platformMaterial);
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
        double x = plugin.getConfig().getDouble("spawn.x", 1.5);
        double y = plugin.getConfig().getDouble("spawn.y", 65.0);
        double z = plugin.getConfig().getDouble("spawn.z", 1.5);
        float yaw = (float) plugin.getConfig().getDouble("spawn.yaw", 0.0);
        float pitch = (float) plugin.getConfig().getDouble("spawn.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
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
