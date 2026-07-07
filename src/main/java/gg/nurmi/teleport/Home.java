package gg.nurmi.teleport;

import gg.nurmi.util.LocationUtil;
import org.bukkit.Location;

public record Home(String name, String world, double x, double y, double z, float yaw, float pitch) {

    /** Null if the stored world isn't currently loaded. */
    public Location toLocation() {
        return LocationUtil.resolve(world, x, y, z, yaw, pitch);
    }
}
