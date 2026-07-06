package gg.nurmi.teleport;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record Home(String name, String world, double x, double y, double z, float yaw, float pitch) {

    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            return null;
        }
        return new Location(bukkitWorld, x, y, z, yaw, pitch);
    }
}
