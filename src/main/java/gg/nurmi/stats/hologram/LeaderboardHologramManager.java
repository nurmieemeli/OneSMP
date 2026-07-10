package gg.nurmi.stats.hologram;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import gg.nurmi.OneSMPPlugin;
import gg.nurmi.stats.StatsManager;
import gg.nurmi.util.Database;
import gg.nurmi.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.joml.Vector3f;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

// OneSMP owns each hologram's location/persistence itself (stored in leaderboard_holograms) and recreates
// every one of them fresh on enable as a non-persistent FancyHolograms entity - this only leans on
// FancyHolograms as a renderer, never as the source of truth for what should exist or where.
public final class LeaderboardHologramManager {

    private static final String NAME_PREFIX = "cs_leaderboard_";

    private record LeaderboardEntry(StatsManager.StatType statType, int limit, Location location) {
    }

    public record HologramInfo(String name, StatsManager.StatType statType, int limit) {
    }

    private record StoredRow(String name, StatsManager.StatType type, int limit, String world,
                              double x, double y, double z, float yaw, float pitch) {
    }

    private final OneSMPPlugin plugin;
    private final Database database;
    private final Map<String, LeaderboardEntry> registry = new ConcurrentHashMap<>();

    public LeaderboardHologramManager(OneSMPPlugin plugin) {
        this.plugin = plugin;
        this.database = plugin.database();
        load();
    }

    private void load() {
        List<StoredRow> rows = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT name, stat_type, entry_limit, world, x, y, z, yaw, pitch FROM leaderboard_holograms");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                StatsManager.StatType type = StatsManager.StatType.fromKey(resultSet.getString("stat_type")).orElse(null);
                if (type == null) {
                    continue;
                }
                rows.add(new StoredRow(resultSet.getString("name"), type, resultSet.getInt("entry_limit"),
                        resultSet.getString("world"), resultSet.getDouble("x"), resultSet.getDouble("y"),
                        resultSet.getDouble("z"), resultSet.getFloat("yaw"), resultSet.getFloat("pitch")));
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load leaderboard holograms", ex);
            return;
        }
        if (rows.isEmpty()) {
            return;
        }

        // The void spawn world and any /world create worlds are only loaded via a scheduled task queued
        // earlier in onEnable() (see SpawnWorldManager/WorldManager), not synchronously - deferring this
        // the same way ensures those worlds actually exist by the time hologram locations are resolved.
        plugin.scheduler().runGlobal(() -> {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            for (StoredRow row : rows) {
                spawnFromRow(manager, row);
            }
        });
    }

    private void spawnFromRow(HologramManager manager, StoredRow row) {
        Location location = row.world() != null
                ? LocationUtil.resolve(row.world(), row.x(), row.y(), row.z(), row.yaw(), row.pitch())
                : null;
        if (location == null) {
            // Pre-migration row with no stored location yet - back-fill it from FancyHolograms' own
            // (still-persistent, at this point) copy if it still has one, so nothing gets lost.
            location = manager.getHologram(row.name()).map(h -> h.getData().getLocation()).orElse(null);
            if (location == null) {
                plugin.getLogger().warning("Leaderboard hologram '" + row.name() + "' has no known location "
                        + "(pre-upgrade entry with no FancyHolograms copy left to recover it from, or its world "
                        + "failed to load) - remove and recreate it with /statshologram.");
                return;
            }
        }

        manager.getHologram(row.name()).ifPresent(manager::removeHologram);
        Hologram hologram = spawn(row.name(), location);
        registry.put(row.name(), new LeaderboardEntry(row.type(), row.limit(), location));
        persist(row.name(), row.type(), row.limit(), location);
        refreshOne(row.name(), hologram, row.type(), row.limit());
    }

    private Hologram spawn(String fancyName, Location location) {
        location.setPitch(0f);
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        TextHologramData data = new TextHologramData(fancyName, location);
        data.setText(new ArrayList<>(List.of(plugin.messages().raw("stats.hologram-loading"))));
        data.setBackground(Hologram.TRANSPARENT);
        data.setBillboard(Display.Billboard.VERTICAL);
        data.setPersistent(false);
        data.setVisibilityDistance(50);
        data.setScale(new Vector3f(1.2f, 1.2f, 1.2f));
        Hologram hologram = manager.create(data);
        manager.addHologram(hologram);
        return hologram;
    }

    public enum CreateResult {
        CREATED, ALREADY_EXISTS
    }

    public CreateResult create(String rawName, Location location, StatsManager.StatType statType, int limit) {
        String fancyName = NAME_PREFIX + rawName.toLowerCase(Locale.ROOT);
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        if (manager.getHologram(fancyName).isPresent()) {
            return CreateResult.ALREADY_EXISTS;
        }

        Hologram hologram = spawn(fancyName, location);
        registry.put(fancyName, new LeaderboardEntry(statType, limit, location));
        persist(fancyName, statType, limit, location);
        refreshOne(fancyName, hologram, statType, limit);
        return CreateResult.CREATED;
    }

    public boolean remove(String rawName) {
        String fancyName = NAME_PREFIX + rawName.toLowerCase(Locale.ROOT);
        if (registry.remove(fancyName) == null) {
            return false;
        }

        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        manager.getHologram(fancyName).ifPresent(manager::removeHologram);
        delete(fancyName);
        return true;
    }

    public List<HologramInfo> list() {
        return registry.entrySet().stream()
                .map(e -> new HologramInfo(e.getKey().substring(NAME_PREFIX.length()), e.getValue().statType(), e.getValue().limit()))
                .toList();
    }

    public void refreshAll() {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        for (Map.Entry<String, LeaderboardEntry> entry : registry.entrySet()) {
            manager.getHologram(entry.getKey()).ifPresent(hologram ->
                    refreshOne(entry.getKey(), hologram, entry.getValue().statType(), entry.getValue().limit()));
        }
    }

    private void refreshOne(String fancyName, Hologram hologram, StatsManager.StatType statType, int limit) {
        plugin.stats().top(statType, limit).thenAccept(entries -> {
            List<String> lines = buildLines(statType, entries, limit);
            Location location = hologram.getData().getLocation();
            plugin.scheduler().runAtLocation(location, () -> {
                if (hologram.getData() instanceof TextHologramData textData) {
                    textData.setText(lines);
                    hologram.forceUpdate();
                }
            });
        });
    }

    // Always renders exactly `limit` lines, padding unfilled ranks with N/A so the hologram doesn't visually shrink over time.
    private List<String> buildLines(StatsManager.StatType statType, List<StatsManager.TopEntry> entries, int limit) {
        List<String> lines = new ArrayList<>();
        lines.add(statType.title(plugin));
        for (int i = 0; i < limit; i++) {
            if (i < entries.size()) {
                StatsManager.TopEntry entry = entries.get(i);
                String name = entry.name() == null ? plugin.messages().raw("general.unknown-name") : entry.name();
                lines.add(plugin.messages().raw("stats.hologram-line")
                        .replace("<rank>", String.valueOf(i + 1))
                        .replace("<name>", name)
                        .replace("<value>", statType.formatValue(plugin, entry.value())));
            } else {
                String naLabel = plugin.messages().raw("stats.hologram-empty-label");
                lines.add(plugin.messages().raw("stats.hologram-line")
                        .replace("<rank>", String.valueOf(i + 1))
                        .replace("<name>", naLabel)
                        .replace("<value>", naLabel));
            }
        }
        return lines;
    }

    private void persist(String fancyName, StatsManager.StatType statType, int limit, Location location) {
        plugin.scheduler().runAsync(() -> {
            String upsert = database.isMysql()
                    ? "INSERT INTO leaderboard_holograms(name, stat_type, entry_limit, world, x, y, z, yaw, pitch) "
                      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                      + "ON DUPLICATE KEY UPDATE stat_type = VALUES(stat_type), entry_limit = VALUES(entry_limit), "
                      + "world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)"
                    : "INSERT INTO leaderboard_holograms(name, stat_type, entry_limit, world, x, y, z, yaw, pitch) "
                      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                      + "ON CONFLICT(name) DO UPDATE SET stat_type = excluded.stat_type, entry_limit = excluded.entry_limit, "
                      + "world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch";
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, fancyName);
                statement.setString(2, statType.name());
                statement.setInt(3, limit);
                statement.setString(4, location.getWorld().getName());
                statement.setDouble(5, location.getX());
                statement.setDouble(6, location.getY());
                statement.setDouble(7, location.getZ());
                statement.setFloat(8, location.getYaw());
                statement.setFloat(9, location.getPitch());
                statement.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to persist leaderboard hologram " + fancyName, ex);
            }
        });
    }

    private void delete(String fancyName) {
        plugin.scheduler().runAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM leaderboard_holograms WHERE name = ?")) {
                statement.setString(1, fancyName);
                statement.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to delete leaderboard hologram " + fancyName, ex);
            }
        });
    }
}
