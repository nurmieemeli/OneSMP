package gg.nurmi.teleport;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.storage.Database;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class WarpManager {

    private final CanvasSuitePlugin plugin;
    private final Database database;

    public WarpManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
        this.database = plugin.database();
    }

    public CompletableFuture<List<Warp>> listWarps() {
        return plugin.scheduler().supplyAsync(() -> {
            List<Warp> warps = new ArrayList<>();
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT name, world, x, y, z, yaw, pitch, created_by FROM warps ORDER BY name")) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        warps.add(new Warp(
                                resultSet.getString("name"), resultSet.getString("world"),
                                resultSet.getDouble("x"), resultSet.getDouble("y"), resultSet.getDouble("z"),
                                resultSet.getFloat("yaw"), resultSet.getFloat("pitch"), resultSet.getString("created_by")));
                    }
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to list warps", ex);
            }
            return warps;
        });
    }

    public CompletableFuture<Optional<Warp>> getWarp(String name) {
        return listWarps().thenApply(warps -> warps.stream()
                .filter(warp -> warp.name().equalsIgnoreCase(name))
                .findFirst());
    }

    public CompletableFuture<Void> setWarp(String name, Location location, UUID createdBy) {
        return plugin.scheduler().supplyAsync(() -> {
            String upsert = database.isMysql()
                    ? "INSERT INTO warps(name, world, x, y, z, yaw, pitch, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)"
                    : "INSERT INTO warps(name, world, x, y, z, yaw, pitch, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(name) DO UPDATE SET world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch";
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, name);
                statement.setString(2, location.getWorld().getName());
                statement.setDouble(3, location.getX());
                statement.setDouble(4, location.getY());
                statement.setDouble(5, location.getZ());
                statement.setFloat(6, location.getYaw());
                statement.setFloat(7, location.getPitch());
                statement.setString(8, createdBy.toString());
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to set warp '" + name + "'", ex);
            }
            return null;
        });
    }

    public CompletableFuture<Boolean> deleteWarp(String name) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM warps WHERE name = ?")) {
                statement.setString(1, name);
                return statement.executeUpdate() > 0;
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to delete warp '" + name + "'", ex);
            }
        });
    }
}
