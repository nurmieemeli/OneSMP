package gg.nurmi.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public final class LocationUtil {

    private LocationUtil() {
    }

    /**
     * Whether a player could safely stand at this location: solid, non-hazardous ground with
     * two air blocks above it. Safe to call off the region thread against a {@link org.bukkit.Chunk}
     * that has already been loaded (e.g. via an async chunk snapshot), since it only reads blocks.
     */
    public static boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);

        if (!feet.getType().isAir() || !head.getType().isAir()) {
            return false;
        }
        Material groundType = ground.getType();
        return ground.getType().isSolid()
                && groundType != Material.LAVA
                && groundType != Material.MAGMA_BLOCK
                && groundType != Material.CACTUS
                && groundType != Material.WATER;
    }

    /** Shared by every stored location record (Home, Warp, ...); null if the world isn't currently loaded. */
    public static Location resolve(String worldName, double x, double y, double z, float yaw, float pitch) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }
}
