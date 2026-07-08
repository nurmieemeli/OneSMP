package gg.nurmi.economy;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.storage.Database;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class EconomyManager {

    public record BalanceEntry(UUID uuid, String name, BigDecimal balance) {
    }

    private final CanvasSuitePlugin plugin;
    private final Database database;
    private final ConcurrentHashMap<UUID, BigDecimal> cache = new ConcurrentHashMap<>();
    private final BigDecimal startingBalance;
    private final BigDecimal maxBalance;

    public EconomyManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
        this.database = plugin.database();
        this.startingBalance = BigDecimal.valueOf(plugin.getConfig().getDouble("economy.starting-balance", 100))
                .setScale(2, RoundingMode.HALF_UP);
        this.maxBalance = BigDecimal.valueOf(plugin.getConfig().getDouble("economy.max-balance", 1_000_000_000))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public String currencySymbol() {
        return plugin.getConfig().getString("economy.currency-symbol", "$");
    }

    public String format(BigDecimal amount) {
        return currencySymbol() + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public BigDecimal getCached(UUID uuid) {
        return cache.getOrDefault(uuid, BigDecimal.ZERO);
    }

    public CompletableFuture<BigDecimal> getBalance(UUID uuid) {
        BigDecimal cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return plugin.scheduler().supplyAsync(() -> loadOrCreate(uuid, null));
    }

    public void handleJoin(UUID uuid, String name) {
        plugin.scheduler().runAsync(() -> cache.put(uuid, loadOrCreate(uuid, name)));
    }

    public void handleQuit(UUID uuid) {
        cache.remove(uuid);
    }

    private BigDecimal loadOrCreate(UUID uuid, String knownName) {
        try (Connection connection = database.getConnection()) {
            try (PreparedStatement select = connection.prepareStatement("SELECT balance FROM accounts WHERE uuid = ?")) {
                select.setString(1, uuid.toString());
                try (ResultSet resultSet = select.executeQuery()) {
                    if (resultSet.next()) {
                        if (knownName != null) {
                            updateName(connection, uuid, knownName);
                        }
                        return resultSet.getBigDecimal("balance").setScale(2, RoundingMode.HALF_UP);
                    }
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO accounts(uuid, name, balance) VALUES (?, ?, ?)")) {
                insert.setString(1, uuid.toString());
                insert.setString(2, knownName);
                insert.setBigDecimal(3, startingBalance);
                insert.executeUpdate();
            }
            return startingBalance;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load balance for " + uuid, ex);
            throw new RuntimeException("Failed to load balance for " + uuid, ex);
        }
    }

    private void updateName(Connection connection, UUID uuid, String name) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE accounts SET name = ? WHERE uuid = ?")) {
            update.setString(1, name);
            update.setString(2, uuid.toString());
            update.executeUpdate();
        }
    }

    public CompletableFuture<Boolean> withdraw(UUID uuid, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        return getBalance(uuid).thenApply(ignored -> {
            AtomicBoolean success = new AtomicBoolean(false);
            cache.compute(uuid, (id, balance) -> {
                BigDecimal current = balance == null ? BigDecimal.ZERO : balance;
                if (current.compareTo(amount) < 0) {
                    return current;
                }
                success.set(true);
                return current.subtract(amount).setScale(2, RoundingMode.HALF_UP);
            });
            if (success.get()) {
                persist(uuid);
            }
            return success.get();
        });
    }

    public CompletableFuture<Void> deposit(UUID uuid, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return getBalance(uuid).thenAccept(ignored -> {
            cache.compute(uuid, (id, balance) -> {
                BigDecimal current = balance == null ? BigDecimal.ZERO : balance;
                return current.add(amount).setScale(2, RoundingMode.HALF_UP).min(maxBalance);
            });
            persist(uuid);
        });
    }

    public void setBalance(UUID uuid, BigDecimal amount) {
        BigDecimal clamped = amount.max(BigDecimal.ZERO).min(maxBalance).setScale(2, RoundingMode.HALF_UP);
        cache.put(uuid, clamped);
        persist(uuid);
    }

    private void persist(UUID uuid) {
        BigDecimal snapshot = cache.get(uuid);
        if (snapshot == null) {
            return;
        }
        plugin.scheduler().runAsync(() -> {
            String upsert = database.isMysql()
                    ? "INSERT INTO accounts(uuid, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE balance = VALUES(balance)"
                    : "INSERT INTO accounts(uuid, balance) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance";
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, uuid.toString());
                statement.setBigDecimal(2, snapshot);
                statement.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to persist balance for " + uuid, ex);
            }
        });
    }

    public CompletableFuture<UUID> resolveUuidByName(String name) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT uuid FROM accounts WHERE name = ?")) {
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

    public CompletableFuture<List<BalanceEntry>> topBalances(int limit) {
        return plugin.scheduler().supplyAsync(() -> {
            List<BalanceEntry> entries = new ArrayList<>();
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT uuid, name, balance FROM accounts WHERE name IS NOT NULL ORDER BY balance DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        entries.add(new BalanceEntry(
                                UUID.fromString(resultSet.getString("uuid")),
                                resultSet.getString("name"),
                                resultSet.getBigDecimal("balance").setScale(2, RoundingMode.HALF_UP)));
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load baltop", ex);
            }
            return entries;
        });
    }
}
