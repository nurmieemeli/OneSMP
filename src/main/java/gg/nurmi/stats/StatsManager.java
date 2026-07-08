package gg.nurmi.stats;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.util.Database;
import gg.nurmi.util.TextUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class StatsManager {

    public enum StatType {
        KILLS("kills"),
        DEATHS("deaths"),
        KILLSTREAK("best_killstreak"),
        PLAYTIME("playtime_seconds");

        private final String column;

        StatType(String column) {
            this.column = column;
        }

        public static Optional<StatType> fromKey(String key) {
            try {
                return Optional.of(StatType.valueOf(key.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }

        public String title() {
            return switch (this) {
                case KILLS -> "<gradient:#facc15:#fbbf24><bold>Kills Leaderboard</bold></gradient>";
                case DEATHS -> "<gradient:#f87171:#ef4444><bold>Deaths Leaderboard</bold></gradient>";
                case KILLSTREAK -> "<gradient:#fb923c:#f97316><bold>Killstreak Leaderboard</bold></gradient>";
                case PLAYTIME -> "<gradient:#38bdf8:#818cf8><bold>Playtime Leaderboard</bold></gradient>";
            };
        }

        public String formatValue(long value) {
            return switch (this) {
                case PLAYTIME -> TextUtil.formatDuration(value);
                case KILLS -> value + " kills";
                case DEATHS -> value + " deaths";
                case KILLSTREAK -> "best streak: " + value;
            };
        }
    }

    public record TopEntry(UUID uuid, String name, long value) {
    }

    public record Snapshot(String name, int kills, int deaths, int currentKillstreak, int bestKillstreak, long playtimeSeconds) {
    }

    public static final Snapshot EMPTY_SNAPSHOT = new Snapshot(null, 0, 0, 0, 0, 0);

    private static final class Stats {
        volatile String name;
        final AtomicInteger kills = new AtomicInteger();
        final AtomicInteger deaths = new AtomicInteger();
        final AtomicInteger currentKillstreak = new AtomicInteger();
        final AtomicInteger bestKillstreak = new AtomicInteger();
        final AtomicLong playtimeSeconds = new AtomicLong();
        volatile long sessionStartMillis;
    }

    private final CanvasSuitePlugin plugin;
    private final Database database;
    private final Map<UUID, Stats> cache = new ConcurrentHashMap<>();

    public StatsManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
        this.database = plugin.database();
    }

    public void handleJoin(UUID uuid, String name) {
        plugin.scheduler().runAsync(() -> {
            Stats stats = loadOrCreate(uuid, name);
            stats.sessionStartMillis = System.currentTimeMillis();
            cache.put(uuid, stats);
        });
    }

    public void handleQuit(UUID uuid) {
        Stats stats = cache.remove(uuid);
        if (stats == null) {
            return;
        }
        accruePlaytime(stats, System.currentTimeMillis());
        persist(uuid, stats);
    }

    public void flushOnline() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Stats> entry : cache.entrySet()) {
            accruePlaytime(entry.getValue(), now);
            persist(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Same as {@link #flushOnline()} but writes synchronously on the calling thread instead of
     * scheduling an async task. Scheduling any new task (even async) from {@code onDisable()}
     * throws {@code IllegalPluginAccessException} - the plugin is already considered disabled by
     * the time that callback runs - so this is the only safe way to flush on shutdown.
     */
    public void flushOnlineBlocking() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Stats> entry : cache.entrySet()) {
            accruePlaytime(entry.getValue(), now);
            persistSync(entry.getKey(), entry.getValue());
        }
    }

    private void accruePlaytime(Stats stats, long now) {
        long elapsedSeconds = Math.max(0, (now - stats.sessionStartMillis) / 1000);
        if (elapsedSeconds > 0) {
            stats.playtimeSeconds.addAndGet(elapsedSeconds);
            stats.sessionStartMillis = now;
        }
    }

    /**
     * Returns the killstreak the killer is now on, for milestone broadcasts.
     */
    public int recordKill(UUID killer, String killerName) {
        Stats stats = cache.computeIfAbsent(killer, ignored -> loadOrCreate(killer, killerName));
        stats.kills.incrementAndGet();
        int streak = stats.currentKillstreak.incrementAndGet();
        stats.bestKillstreak.updateAndGet(best -> Math.max(best, streak));
        persist(killer, stats);
        return streak;
    }

    public void recordDeath(UUID victim, String victimName) {
        Stats stats = cache.computeIfAbsent(victim, ignored -> loadOrCreate(victim, victimName));
        stats.deaths.incrementAndGet();
        stats.currentKillstreak.set(0);
        persist(victim, stats);
    }

    public CompletableFuture<Snapshot> getSnapshot(UUID uuid) {
        Snapshot cached = getLiveSnapshot(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return plugin.scheduler().supplyAsync(() -> loadSnapshotFromDb(uuid));
    }

    /**
     * Synchronous, non-blocking read of an online player's current stats - safe to call from a
     * MiniMessage placeholder resolver. Returns null if the player isn't cached (i.e. offline).
     */
    public Snapshot getLiveSnapshot(UUID uuid) {
        Stats cached = cache.get(uuid);
        if (cached == null) {
            return null;
        }
        long liveSeconds = cached.playtimeSeconds.get()
                + Math.max(0, (System.currentTimeMillis() - cached.sessionStartMillis) / 1000);
        return new Snapshot(cached.name, cached.kills.get(), cached.deaths.get(),
                cached.currentKillstreak.get(), cached.bestKillstreak.get(), liveSeconds);
    }

    private Snapshot loadSnapshotFromDb(UUID uuid) {
        try (Connection connection = database.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT name, kills, deaths, current_killstreak, best_killstreak, playtime_seconds FROM player_stats WHERE uuid = ?")) {
            select.setString(1, uuid.toString());
            try (ResultSet resultSet = select.executeQuery()) {
                if (resultSet.next()) {
                    return new Snapshot(resultSet.getString("name"), resultSet.getInt("kills"), resultSet.getInt("deaths"),
                            resultSet.getInt("current_killstreak"), resultSet.getInt("best_killstreak"),
                            resultSet.getLong("playtime_seconds"));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load stats for " + uuid, ex);
        }
        return EMPTY_SNAPSHOT;
    }

    private Stats loadOrCreate(UUID uuid, String knownName) {
        try (Connection connection = database.getConnection()) {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT name, kills, deaths, current_killstreak, best_killstreak, playtime_seconds FROM player_stats WHERE uuid = ?")) {
                select.setString(1, uuid.toString());
                try (ResultSet resultSet = select.executeQuery()) {
                    if (resultSet.next()) {
                        Stats stats = new Stats();
                        stats.name = knownName != null ? knownName : resultSet.getString("name");
                        stats.kills.set(resultSet.getInt("kills"));
                        stats.deaths.set(resultSet.getInt("deaths"));
                        stats.currentKillstreak.set(resultSet.getInt("current_killstreak"));
                        stats.bestKillstreak.set(resultSet.getInt("best_killstreak"));
                        stats.playtimeSeconds.set(resultSet.getLong("playtime_seconds"));
                        if (knownName != null) {
                            updateName(connection, uuid, knownName);
                        }
                        return stats;
                    }
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO player_stats(uuid, name) VALUES (?, ?)")) {
                insert.setString(1, uuid.toString());
                insert.setString(2, knownName);
                insert.executeUpdate();
            }
            Stats stats = new Stats();
            stats.name = knownName;
            return stats;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load stats for " + uuid, ex);
            throw new RuntimeException("Failed to load stats for " + uuid, ex);
        }
    }

    private void updateName(Connection connection, UUID uuid, String name) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE player_stats SET name = ? WHERE uuid = ?")) {
            update.setString(1, name);
            update.setString(2, uuid.toString());
            update.executeUpdate();
        }
    }

    private void persist(UUID uuid, Stats stats) {
        plugin.scheduler().runAsync(() -> persistSync(uuid, stats));
    }

    private void persistSync(UUID uuid, Stats stats) {
        String upsert = database.isMysql()
                ? """
                  INSERT INTO player_stats(uuid, name, kills, deaths, current_killstreak, best_killstreak, playtime_seconds)
                  VALUES (?, ?, ?, ?, ?, ?, ?)
                  ON DUPLICATE KEY UPDATE name = VALUES(name), kills = VALUES(kills), deaths = VALUES(deaths),
                      current_killstreak = VALUES(current_killstreak), best_killstreak = VALUES(best_killstreak),
                      playtime_seconds = VALUES(playtime_seconds)
                  """
                : """
                  INSERT INTO player_stats(uuid, name, kills, deaths, current_killstreak, best_killstreak, playtime_seconds)
                  VALUES (?, ?, ?, ?, ?, ?, ?)
                  ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, kills = excluded.kills, deaths = excluded.deaths,
                      current_killstreak = excluded.current_killstreak, best_killstreak = excluded.best_killstreak,
                      playtime_seconds = excluded.playtime_seconds
                  """;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, stats.name);
            statement.setInt(3, stats.kills.get());
            statement.setInt(4, stats.deaths.get());
            statement.setInt(5, stats.currentKillstreak.get());
            statement.setInt(6, stats.bestKillstreak.get());
            statement.setLong(7, stats.playtimeSeconds.get());
            statement.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to persist stats for " + uuid, ex);
        }
    }

    public CompletableFuture<UUID> resolveUuidByName(String name) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT uuid FROM player_stats WHERE name = ?")) {
                statement.setString(1, name);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? UUID.fromString(resultSet.getString("uuid")) : null;
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to resolve UUID for name " + name, ex);
                throw new RuntimeException(ex);
            }
        });
    }

    public CompletableFuture<List<TopEntry>> top(StatType type, int limit) {
        return plugin.scheduler().supplyAsync(() -> {
            List<TopEntry> entries = new ArrayList<>();
            String sql = "SELECT uuid, name, " + type.column + " AS value FROM player_stats WHERE name IS NOT NULL ORDER BY "
                    + type.column + " DESC LIMIT ?";
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        entries.add(new TopEntry(UUID.fromString(resultSet.getString("uuid")),
                                resultSet.getString("name"), resultSet.getLong("value")));
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load " + type + " leaderboard", ex);
            }
            return entries;
        });
    }
}
