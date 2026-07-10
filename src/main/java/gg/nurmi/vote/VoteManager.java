package gg.nurmi.vote;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.util.Database;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class VoteManager {

    public record TopEntry(UUID uuid, String name, long value) {
    }

    public record Reward(BigDecimal money, String crateType, int crateKeyAmount) {
    }

    private record VoteResult(int totalVotes, int currentStreak, boolean milestoneReached, int milestoneStreak) {
    }

    private final OneSMPPlugin plugin;
    private final Database database;
    private final Map<UUID, VoteSnapshot> cache = new ConcurrentHashMap<>();

    public VoteManager(OneSMPPlugin plugin) {
        this.plugin = plugin;
        this.database = plugin.database();
    }

    // Loads this player's snapshot into the live cache so MiniPlaceholders can read it without blocking.
    public void handleJoin(UUID uuid) {
        plugin.scheduler().runAsync(() -> cache.put(uuid, loadSnapshotFromDb(uuid)));
    }

    public void handleQuit(UUID uuid) {
        cache.remove(uuid);
    }

    // Synchronous, cache-only read - safe to call from a MiniMessage tag resolver.
    public VoteSnapshot getLiveSnapshot(UUID uuid) {
        return cache.getOrDefault(uuid, VoteSnapshot.EMPTY);
    }

    public void handleVote(String username) {
        if (!plugin.getConfig().getBoolean("vote.enabled", true)) {
            return;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
        UUID uuid = offlinePlayer.getUniqueId();
        String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : username;

        plugin.scheduler().runAsync(() -> {
            VoteResult result = recordVote(uuid, name);
            Reward reward = baseReward();
            grant(uuid, reward);
            if (result.milestoneReached()) {
                Reward bonus = milestoneReward(result.milestoneStreak());
                if (bonus != null) {
                    grant(uuid, bonus);
                }
            }
            plugin.scheduler().runGlobal(() -> announce(uuid, name, result));
        });
    }

    // A same-day repeat vote counts toward the total but doesn't extend the streak; a gap of more than a day resets it.
    private VoteResult recordVote(UUID uuid, String name) {
        long today = LocalDate.now().toEpochDay();
        try (Connection connection = database.getConnection()) {
            int currentStreak;
            int bestStreak;
            long lastVoteEpochDay;
            int totalVotes;

            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT total_votes, current_streak, best_streak, last_vote_epoch_day FROM player_votes WHERE uuid = ?")) {
                select.setString(1, uuid.toString());
                try (ResultSet resultSet = select.executeQuery()) {
                    if (resultSet.next()) {
                        totalVotes = resultSet.getInt("total_votes");
                        currentStreak = resultSet.getInt("current_streak");
                        bestStreak = resultSet.getInt("best_streak");
                        lastVoteEpochDay = resultSet.getLong("last_vote_epoch_day");
                    } else {
                        totalVotes = 0;
                        currentStreak = 0;
                        bestStreak = 0;
                        lastVoteEpochDay = 0;
                    }
                }
            }

            totalVotes++;
            boolean milestoneReached = false;
            if (today == lastVoteEpochDay) {
                // deliberately empty
            } else if (today == lastVoteEpochDay + 1) {
                currentStreak++;
                lastVoteEpochDay = today;
                milestoneReached = milestones().containsKey(currentStreak);
            } else {
                currentStreak = 1;
                lastVoteEpochDay = today;
            }
            bestStreak = Math.max(bestStreak, currentStreak);

            try (PreparedStatement upsert = mysql()
                    ? connection.prepareStatement("""
                        INSERT INTO player_votes (uuid, total_votes, current_streak, best_streak, last_vote_epoch_day)
                        VALUES (?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE total_votes = VALUES(total_votes),
                            current_streak = VALUES(current_streak), best_streak = VALUES(best_streak),
                            last_vote_epoch_day = VALUES(last_vote_epoch_day)
                        """)
                    : connection.prepareStatement("""
                        INSERT INTO player_votes (uuid, total_votes, current_streak, best_streak, last_vote_epoch_day)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET total_votes = excluded.total_votes,
                            current_streak = excluded.current_streak, best_streak = excluded.best_streak,
                            last_vote_epoch_day = excluded.last_vote_epoch_day
                        """)) {
                upsert.setString(1, uuid.toString());
                upsert.setInt(2, totalVotes);
                upsert.setInt(3, currentStreak);
                upsert.setInt(4, bestStreak);
                upsert.setLong(5, lastVoteEpochDay);
                upsert.executeUpdate();
            }

            if (Bukkit.getPlayer(uuid) != null) {
                cache.put(uuid, new VoteSnapshot(totalVotes, currentStreak, bestStreak));
            }
            return new VoteResult(totalVotes, currentStreak, milestoneReached, currentStreak);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to record vote for " + name, ex);
            return new VoteResult(0, 0, false, 0);
        }
    }

    private void grant(UUID uuid, Reward reward) {
        if (reward.money().signum() > 0) {
            plugin.economy().deposit(uuid, reward.money());
        }
        if (reward.crateKeyAmount() > 0) {
            ItemStack key = plugin.crates().createKey(reward.crateType(), reward.crateKeyAmount());
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                plugin.scheduler().runAtEntity(online, () -> giveOrDrop(online, key),
                        () -> plugin.scheduler().runAsync(() -> queuePending(uuid, reward)));
            } else {
                queuePending(uuid, reward);
            }
        }
    }

    private void giveOrDrop(Player player, ItemStack key) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(key);
        for (ItemStack overflow : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
    }

    private void queuePending(UUID uuid, Reward reward) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO pending_vote_rewards (uuid, crate_type, key_amount, created_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, reward.crateType());
            statement.setInt(3, reward.crateKeyAmount());
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to queue pending vote reward for " + uuid, ex);
        }
    }

    // Called on join - delivers any crate keys that were earned while the player was offline.
    public void deliverPending(Player player) {
        plugin.scheduler().runAsync(() -> {
            List<ItemStack> keys = new ArrayList<>();
            try (Connection connection = database.getConnection()) {
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT id, crate_type, key_amount FROM pending_vote_rewards WHERE uuid = ?")) {
                    select.setString(1, player.getUniqueId().toString());
                    try (ResultSet resultSet = select.executeQuery()) {
                        while (resultSet.next()) {
                            keys.add(plugin.crates().createKey(resultSet.getString("crate_type"), resultSet.getInt("key_amount")));
                        }
                    }
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM pending_vote_rewards WHERE uuid = ?")) {
                    delete.setString(1, player.getUniqueId().toString());
                    delete.executeUpdate();
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to deliver pending vote rewards for " + player.getName(), ex);
                return;
            }
            if (keys.isEmpty()) {
                return;
            }
            plugin.scheduler().runAtEntity(player, () -> {
                for (ItemStack key : keys) {
                    giveOrDrop(player, key);
                }
                plugin.messages().send(player, "vote.pending-delivered", Placeholder.unparsed("amount", String.valueOf(keys.size())));
            }, () -> {});
        });
    }

    private void announce(UUID uuid, String name, VoteResult result) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            plugin.messages().send(online, "vote.thanks");
        }
        if (plugin.getConfig().getBoolean("vote.broadcast", true)) {
            Bukkit.broadcast(plugin.messages().render(online != null ? online : Bukkit.getConsoleSender(),
                    "vote.broadcast", Placeholder.unparsed("player", name)));
        }
        if (result.milestoneReached()) {
            Bukkit.broadcast(plugin.messages().render(online != null ? online : Bukkit.getConsoleSender(),
                    "vote.streak-milestone",
                    Placeholder.unparsed("player", name), Placeholder.unparsed("streak", String.valueOf(result.milestoneStreak()))));
        }
    }

    private Reward baseReward() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("vote.reward");
        if (section == null) {
            return new Reward(BigDecimal.ZERO, "common", 0);
        }
        return new Reward(BigDecimal.valueOf(section.getDouble("money", 0)),
                section.getString("crate-type", "common"),
                section.getInt("crate-key-amount", 0));
    }

    private Reward milestoneReward(int streak) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("vote.streak.milestones." + streak);
        if (section == null) {
            return null;
        }
        return new Reward(BigDecimal.valueOf(section.getDouble("money", 0)),
                section.getString("crate-type", baseReward().crateType()),
                section.getInt("crate-key-amount", 0));
    }

    private Map<Integer, ConfigurationSection> milestones() {
        Map<Integer, ConfigurationSection> milestones = new TreeMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("vote.streak.milestones");
        if (section == null) {
            return milestones;
        }
        for (String key : section.getKeys(false)) {
            try {
                milestones.put(Integer.parseInt(key), section.getConfigurationSection(key));
            } catch (NumberFormatException ignored) {
            }
        }
        return milestones;
    }

    private boolean mysql() {
        return plugin.getConfig().getString("storage.type", "SQLITE").equalsIgnoreCase("MYSQL");
    }

    public CompletableFuture<VoteSnapshot> snapshot(UUID uuid) {
        return plugin.scheduler().supplyAsync(() -> loadSnapshotFromDb(uuid));
    }

    private VoteSnapshot loadSnapshotFromDb(UUID uuid) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT total_votes, current_streak, best_streak FROM player_votes WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new VoteSnapshot(resultSet.getInt("total_votes"), resultSet.getInt("current_streak"), resultSet.getInt("best_streak"));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load vote snapshot for " + uuid, ex);
        }
        return VoteSnapshot.EMPTY;
    }

    public record VoteSnapshot(int totalVotes, int currentStreak, int bestStreak) {
        public static final VoteSnapshot EMPTY = new VoteSnapshot(0, 0, 0);
    }

    public CompletableFuture<List<TopEntry>> top(int limit) {
        return plugin.scheduler().supplyAsync(() -> {
            List<TopEntry> entries = new ArrayList<>();
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT uuid, total_votes FROM player_votes ORDER BY total_votes DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                        entries.add(new TopEntry(uuid, Bukkit.getOfflinePlayer(uuid).getName(), resultSet.getLong("total_votes")));
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load vote leaderboard", ex);
            }
            return entries;
        });
    }
}
