package gg.nurmi.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public final class Database {

    private final Plugin plugin;
    private HikariDataSource dataSource;
    private boolean mysql;

    public Database(Plugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        FileConfiguration config = plugin.getConfig();
        String type = config.getString("storage.type", "SQLITE").trim().toUpperCase();

        if (type.equals("MYSQL")) {
            try {
                connectMysql(config);
                mysql = true;
                plugin.getLogger().info("Connected to MySQL storage.");
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Could not connect to MySQL, falling back to local SQLite.", ex);
                connectSqlite(config);
                mysql = false;
            }
        } else {
            connectSqlite(config);
            mysql = false;
        }

        migrate();
    }

    private void connectMysql(FileConfiguration config) {
        String host = config.getString("storage.mysql.host", "localhost");
        int port = config.getInt("storage.mysql.port", 3306);
        String database = config.getString("storage.mysql.database", "onesmp");
        String username = config.getString("storage.mysql.username", "onesmp");
        String password = config.getString("storage.mysql.password", "");
        boolean useSsl = config.getBoolean("storage.mysql.useSSL", false);
        int poolSize = config.getInt("storage.mysql.pool-size", 10);

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl + "&autoReconnect=true&characterEncoding=utf8");
        hikari.setUsername(username);
        hikari.setPassword(password);
        hikari.setMaximumPoolSize(poolSize);
        hikari.setPoolName("OneSMP-MySQL");
        hikari.setConnectionTimeout(5000);
        hikari.setInitializationFailTimeout(5000);
        this.dataSource = new HikariDataSource(hikari);
    }

    private void connectSqlite(FileConfiguration config) {
        if (dataSource != null) {
            dataSource.close();
        }
        File file = new File(plugin.getDataFolder(), config.getString("storage.sqlite.file", "data.db"));
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        hikari.setMaximumPoolSize(1);
        hikari.setPoolName("OneSMP-SQLite");
        this.dataSource = new HikariDataSource(hikari);
    }

    private void migrate() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            String idType = mysql ? "INT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS accounts (
                    uuid VARCHAR(36) PRIMARY KEY,
                    balance DECIMAL(20,2) NOT NULL DEFAULT 0
                )
                """);
            dropColumnIfExists(connection, "accounts", "name");

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS homes (
                    id %s,
                    owner VARCHAR(36) NOT NULL,
                    name VARCHAR(32) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    yaw FLOAT NOT NULL,
                    pitch FLOAT NOT NULL,
                    UNIQUE(owner, name)
                )
                """.formatted(idType));

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS warps (
                    name VARCHAR(32) PRIMARY KEY,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    yaw FLOAT NOT NULL,
                    pitch FLOAT NOT NULL,
                    created_by VARCHAR(36)
                )
                """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS guilds (
                    id %s,
                    name VARCHAR(32) NOT NULL UNIQUE,
                    tag VARCHAR(8) NOT NULL UNIQUE,
                    owner VARCHAR(36) NOT NULL,
                    balance DECIMAL(20,2) NOT NULL DEFAULT 0,
                    member_limit INT NOT NULL DEFAULT 10,
                    home_world VARCHAR(64),
                    home_x DOUBLE,
                    home_y DOUBLE,
                    home_z DOUBLE,
                    home_yaw FLOAT,
                    home_pitch FLOAT,
                    created_at BIGINT NOT NULL
                )
                """.formatted(idType));

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS guild_members (
                    guild_id INTEGER NOT NULL,
                    uuid VARCHAR(36) NOT NULL,
                    role VARCHAR(16) NOT NULL,
                    joined_at BIGINT NOT NULL,
                    PRIMARY KEY (guild_id, uuid)
                )
                """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ignored_players (
                    owner VARCHAR(36) NOT NULL,
                    ignored VARCHAR(36) NOT NULL,
                    PRIMARY KEY (owner, ignored)
                )
                """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    uuid VARCHAR(36) PRIMARY KEY,
                    kills INT NOT NULL DEFAULT 0,
                    deaths INT NOT NULL DEFAULT 0,
                    current_killstreak INT NOT NULL DEFAULT 0,
                    best_killstreak INT NOT NULL DEFAULT 0,
                    playtime_seconds BIGINT NOT NULL DEFAULT 0
                )
                """);
            dropColumnIfExists(connection, "player_stats", "name");

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS leaderboard_holograms (
                    name VARCHAR(48) PRIMARY KEY,
                    stat_type VARCHAR(16) NOT NULL,
                    entry_limit INT NOT NULL DEFAULT 10
                )
                """);
            addColumnIfMissing(connection, "leaderboard_holograms", "world", "VARCHAR(64)");
            addColumnIfMissing(connection, "leaderboard_holograms", "x", "DOUBLE");
            addColumnIfMissing(connection, "leaderboard_holograms", "y", "DOUBLE");
            addColumnIfMissing(connection, "leaderboard_holograms", "z", "DOUBLE");
            addColumnIfMissing(connection, "leaderboard_holograms", "yaw", "FLOAT");
            addColumnIfMissing(connection, "leaderboard_holograms", "pitch", "FLOAT");

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS crates (
                    world VARCHAR(64) NOT NULL,
                    x INT NOT NULL,
                    y INT NOT NULL,
                    z INT NOT NULL,
                    crate_type VARCHAR(32) NOT NULL,
                    created_by VARCHAR(36),
                    PRIMARY KEY (world, x, y, z)
                )
                """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS market_listings (
                    id %s,
                    seller_uuid VARCHAR(36) NOT NULL,
                    material VARCHAR(64) NOT NULL,
                    item_data BLOB NOT NULL,
                    price DECIMAL(20,2) NOT NULL,
                    created_at BIGINT NOT NULL
                )
                """.formatted(idType));
            dropColumnIfExists(connection, "market_listings", "seller_name");

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_votes (
                    uuid VARCHAR(36) PRIMARY KEY,
                    total_votes INT NOT NULL DEFAULT 0,
                    current_streak INT NOT NULL DEFAULT 0,
                    best_streak INT NOT NULL DEFAULT 0,
                    last_vote_epoch_day BIGINT NOT NULL DEFAULT 0
                )
                """);
            dropColumnIfExists(connection, "player_votes", "name");

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pending_vote_rewards (
                    id %s,
                    uuid VARCHAR(36) NOT NULL,
                    crate_type VARCHAR(32) NOT NULL,
                    key_amount INT NOT NULL,
                    created_at BIGINT NOT NULL
                )
                """.formatted(idType));
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to run OneSMP schema migration", ex);
        }
    }

    // Portable across SQLite/MySQL without relying on ADD COLUMN IF NOT EXISTS syntax support varying by version.
    private void addColumnIfMissing(Connection connection, String table, String column, String columnDefinition) throws SQLException {
        try (var columns = connection.getMetaData().getColumns(null, null, table, column)) {
            if (columns.next()) {
                return;
            }
        }
        try (Statement alter = connection.createStatement()) {
            alter.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + columnDefinition);
        }
    }

    // Drops a column left over from a previous schema version (e.g. a persisted player-name column we no longer want -
    // names must only ever be resolved live via Bukkit, never stored as the way to identify a player's data).
    private void dropColumnIfExists(Connection connection, String table, String column) throws SQLException {
        try (var columns = connection.getMetaData().getColumns(null, null, table, column)) {
            if (!columns.next()) {
                return;
            }
        }
        try (Statement alter = connection.createStatement()) {
            alter.executeUpdate("ALTER TABLE " + table + " DROP COLUMN " + column);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public boolean isMysql() {
        return mysql;
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
