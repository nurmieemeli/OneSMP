package gg.nurmi.stats.hologram;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.stats.StatsManager;
import gg.nurmi.util.Database;
import org.bukkit.Location;

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

/**
 * Wraps the FancyHolograms API to create/track text holograms whose lines are periodically
 * repopulated from {@link StatsManager} leaderboard queries. FancyHolograms owns the hologram
 * entity itself (location, persistence across restarts); we only remember which of its holograms
 * are ours and which stat leaderboard each one shows, so we can push fresh text into them.
 */
public final class LeaderboardHologramManager {

    private static final String NAME_PREFIX = "cs_leaderboard_";

    private record LeaderboardEntry(StatsManager.StatType statType, int limit) {
    }

    public record HologramInfo(String name, StatsManager.StatType statType, int limit) {
    }

    private final CanvasSuitePlugin plugin;
    private final Database database;
    private final Map<String, LeaderboardEntry> registry = new ConcurrentHashMap<>();

    public LeaderboardHologramManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
        this.database = plugin.database();
        load();
    }

    private void load() {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT name, stat_type, entry_limit FROM leaderboard_holograms");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String name = resultSet.getString("name");
                int limit = resultSet.getInt("entry_limit");
                StatsManager.StatType.fromKey(resultSet.getString("stat_type"))
                        .ifPresent(type -> registry.put(name, new LeaderboardEntry(type, limit)));
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load leaderboard holograms", ex);
        }
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

        TextHologramData data = new TextHologramData(fancyName, location);
        data.setText(new ArrayList<>(List.of("<gray>Loading...")));
        Hologram hologram = manager.create(data);
        manager.addHologram(hologram);

        registry.put(fancyName, new LeaderboardEntry(statType, limit));
        persist(fancyName, statType, limit);
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

    /**
     * Always renders exactly {@code limit} entry lines, regardless of how many players actually
     * have tracked stats yet - slots past the end of {@code entries} are padded with the same
     * "#rank name - value" style, just with name/value shown as N/A, so the hologram doesn't
     * visually shrink/jump as more players get tracked over time.
     */
    private List<String> buildLines(StatsManager.StatType statType, List<StatsManager.TopEntry> entries, int limit) {
        List<String> lines = new ArrayList<>();
        lines.add(statType.title());
        for (int i = 0; i < limit; i++) {
            if (i < entries.size()) {
                StatsManager.TopEntry entry = entries.get(i);
                String name = entry.name() == null ? "?" : entry.name();
                lines.add("<gray>#" + (i + 1) + " <white>" + name + "</white> <dark_gray>-</dark_gray> <green>"
                        + statType.formatValue(entry.value()));
            } else {
                lines.add("<gray>#" + (i + 1) + " <white>N/A</white> <dark_gray>-</dark_gray> <green>N/A");
            }
        }
        return lines;
    }

    private void persist(String fancyName, StatsManager.StatType statType, int limit) {
        plugin.scheduler().runAsync(() -> {
            String upsert = database.isMysql()
                    ? "INSERT INTO leaderboard_holograms(name, stat_type, entry_limit) VALUES (?, ?, ?) "
                      + "ON DUPLICATE KEY UPDATE stat_type = VALUES(stat_type), entry_limit = VALUES(entry_limit)"
                    : "INSERT INTO leaderboard_holograms(name, stat_type, entry_limit) VALUES (?, ?, ?) "
                      + "ON CONFLICT(name) DO UPDATE SET stat_type = excluded.stat_type, entry_limit = excluded.entry_limit";
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, fancyName);
                statement.setString(2, statType.name());
                statement.setInt(3, limit);
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
