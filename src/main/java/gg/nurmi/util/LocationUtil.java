package gg.nurmi.util;

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

    public static String serialize(Location location) {
        return location.getWorld().getName() + ";" + location.getX() + ";" + location.getY() + ";"
                + location.getZ() + ";" + location.getYaw() + ";" + location.getPitch();
    }
}
