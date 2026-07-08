package gg.nurmi.teleport;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.util.Database;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class HomeManager {

    public enum SetHomeResult {
        CREATED, UPDATED, LIMIT_REACHED
    }

    private final CanvasSuitePlugin plugin;
    private final Database database;

    public HomeManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
        this.database = plugin.database();
    }

    public int resolveLimit(Player player) {
        if (player.hasPermission("canvassuite.home.unlimited")) {
            return Integer.MAX_VALUE;
        }
        String prefix = plugin.getConfig().getString("teleport.home-limit-permission-prefix", "canvassuite.home.limit.");
        int limit = plugin.getConfig().getInt("teleport.default-home-limit", 3);
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue() || !info.getPermission().startsWith(prefix)) {
                continue;
            }
            try {
                limit = Math.max(limit, Integer.parseInt(info.getPermission().substring(prefix.length())));
            } catch (NumberFormatException ignored) {
            }
        }
        return limit;
    }

    public CompletableFuture<List<Home>> listHomes(UUID owner) {
        return plugin.scheduler().supplyAsync(() -> {
            List<Home> homes = new ArrayList<>();
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT name, world, x, y, z, yaw, pitch FROM homes WHERE owner = ? ORDER BY name")) {
                statement.setString(1, owner.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        homes.add(new Home(
                                resultSet.getString("name"), resultSet.getString("world"),
                                resultSet.getDouble("x"), resultSet.getDouble("y"), resultSet.getDouble("z"),
                                resultSet.getFloat("yaw"), resultSet.getFloat("pitch")));
                    }
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to list homes for " + owner, ex);
            }
            return homes;
        });
    }

    public CompletableFuture<Optional<Home>> getHome(UUID owner, String name) {
        return listHomes(owner).thenApply(homes -> homes.stream()
                .filter(home -> home.name().equalsIgnoreCase(name))
                .findFirst());
    }

    public CompletableFuture<SetHomeResult> setHome(UUID owner, String name, Location location, int limit) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection()) {
                boolean exists;
                try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM homes WHERE owner = ? AND name = ?")) {
                    check.setString(1, owner.toString());
                    check.setString(2, name);
                    try (ResultSet resultSet = check.executeQuery()) {
                        exists = resultSet.next();
                    }
                }

                if (!exists) {
                    int count;
                    try (PreparedStatement countStatement = connection.prepareStatement("SELECT COUNT(*) FROM homes WHERE owner = ?")) {
                        countStatement.setString(1, owner.toString());
                        try (ResultSet resultSet = countStatement.executeQuery()) {
                            resultSet.next();
                            count = resultSet.getInt(1);
                        }
                    }
                    if (count >= limit) {
                        return SetHomeResult.LIMIT_REACHED;
                    }
                }

                String upsert = database.isMysql()
                        ? "INSERT INTO homes(owner, name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)"
                        : "INSERT INTO homes(owner, name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(owner, name) DO UPDATE SET world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch";
                try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                    statement.setString(1, owner.toString());
                    statement.setString(2, name);
                    statement.setString(3, location.getWorld().getName());
                    statement.setDouble(4, location.getX());
                    statement.setDouble(5, location.getY());
                    statement.setDouble(6, location.getZ());
                    statement.setFloat(7, location.getYaw());
                    statement.setFloat(8, location.getPitch());
                    statement.executeUpdate();
                }
                return exists ? SetHomeResult.UPDATED : SetHomeResult.CREATED;
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to set home '" + name + "' for " + owner, ex);
            }
        });
    }

    public CompletableFuture<Boolean> deleteHome(UUID owner, String name) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM homes WHERE owner = ? AND name = ?")) {
                statement.setString(1, owner.toString());
                statement.setString(2, name);
                return statement.executeUpdate() > 0;
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to delete home '" + name + "' for " + owner, ex);
            }
        });
    }
}
