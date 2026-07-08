package gg.nurmi.guild;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.util.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class GuildManager {

    public enum CreateResult {
        SUCCESS, NAME_TAKEN, TAG_TAKEN, INVALID_NAME, INVALID_TAG
    }

    private final CanvasSuitePlugin plugin;
    private final Database database;
    private final Map<UUID, Integer> pendingInvites = new ConcurrentHashMap<>();
    private final Map<UUID, Guild> guildCache = new ConcurrentHashMap<>();

    public GuildManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
        this.database = plugin.database();
    }

    /**
     * Synchronous, non-blocking read of an online player's current guild - safe to call from a
     * MiniMessage placeholder resolver. Returns empty if the player isn't cached (offline or not
     * in a guild).
     */
    public Optional<Guild> getCachedGuild(UUID uuid) {
        return Optional.ofNullable(guildCache.get(uuid));
    }

    public void handleJoin(UUID uuid) {
        refreshCache(uuid);
    }

    public void handleQuit(UUID uuid) {
        guildCache.remove(uuid);
    }

    /**
     * Re-reads a single player's guild membership from storage and updates the cache used by
     * {@link #getCachedGuild(UUID)} - call this after any change to that player's membership
     * (create/join/kick/leave/disband) so cached placeholders don't go stale. No-ops if the
     * player isn't online, since the cache only ever tracks online players.
     */
    public void refreshCache(UUID uuid) {
        if (Bukkit.getPlayer(uuid) == null) {
            guildCache.remove(uuid);
            return;
        }
        getGuildByMember(uuid).thenAccept(optionalGuild -> {
            if (optionalGuild.isPresent()) {
                guildCache.put(uuid, optionalGuild.get());
            } else {
                guildCache.remove(uuid);
            }
        });
    }

    public void invite(UUID target, int guildId) {
        pendingInvites.put(target, guildId);
    }

    public Integer pendingInvite(UUID target) {
        return pendingInvites.get(target);
    }

    public void clearInvite(UUID target) {
        pendingInvites.remove(target);
    }

    public CompletableFuture<CreateResult> createGuild(UUID owner, String name, String tag) {
        int minLen = plugin.getConfig().getInt("guild.min-name-length", 3);
        int maxLen = plugin.getConfig().getInt("guild.max-name-length", 16);
        int maxTag = plugin.getConfig().getInt("guild.max-tag-length", 5);
        if (name.length() < minLen || name.length() > maxLen) {
            return CompletableFuture.completedFuture(CreateResult.INVALID_NAME);
        }
        if (tag.isEmpty() || tag.length() > maxTag) {
            return CompletableFuture.completedFuture(CreateResult.INVALID_TAG);
        }

        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection()) {
                try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM guilds WHERE name = ?")) {
                    check.setString(1, name);
                    try (ResultSet resultSet = check.executeQuery()) {
                        if (resultSet.next()) {
                            return CreateResult.NAME_TAKEN;
                        }
                    }
                }
                try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM guilds WHERE tag = ?")) {
                    check.setString(1, tag);
                    try (ResultSet resultSet = check.executeQuery()) {
                        if (resultSet.next()) {
                            return CreateResult.TAG_TAKEN;
                        }
                    }
                }

                int memberLimit = plugin.getConfig().getInt("guild.default-member-limit", 10);
                int guildId;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO guilds(name, tag, owner, balance, member_limit, created_at) VALUES (?, ?, ?, 0, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, name);
                    insert.setString(2, tag);
                    insert.setString(3, owner.toString());
                    insert.setInt(4, memberLimit);
                    insert.setLong(5, System.currentTimeMillis());
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        keys.next();
                        guildId = keys.getInt(1);
                    }
                }
                try (PreparedStatement insertMember = connection.prepareStatement(
                        "INSERT INTO guild_members(guild_id, uuid, role, joined_at) VALUES (?, ?, ?, ?)")) {
                    insertMember.setInt(1, guildId);
                    insertMember.setString(2, owner.toString());
                    insertMember.setString(3, GuildRole.OWNER.name());
                    insertMember.setLong(4, System.currentTimeMillis());
                    insertMember.executeUpdate();
                }
                return CreateResult.SUCCESS;
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to create guild '" + name + "'", ex);
            }
        });
    }

    public CompletableFuture<Boolean> disbandGuild(int guildId) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection()) {
                try (PreparedStatement deleteMembers = connection.prepareStatement("DELETE FROM guild_members WHERE guild_id = ?")) {
                    deleteMembers.setInt(1, guildId);
                    deleteMembers.executeUpdate();
                }
                try (PreparedStatement deleteGuild = connection.prepareStatement("DELETE FROM guilds WHERE id = ?")) {
                    deleteGuild.setInt(1, guildId);
                    return deleteGuild.executeUpdate() > 0;
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to disband guild " + guildId, ex);
            }
        });
    }

    public CompletableFuture<Void> addMember(int guildId, UUID uuid, GuildRole role) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO guild_members(guild_id, uuid, role, joined_at) VALUES (?, ?, ?, ?)")) {
                statement.setInt(1, guildId);
                statement.setString(2, uuid.toString());
                statement.setString(3, role.name());
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to add member " + uuid + " to guild " + guildId, ex);
            }
            return null;
        });
    }

    public CompletableFuture<Void> removeMember(int guildId, UUID uuid) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM guild_members WHERE guild_id = ? AND uuid = ?")) {
                statement.setInt(1, guildId);
                statement.setString(2, uuid.toString());
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to remove member " + uuid + " from guild " + guildId, ex);
            }
            return null;
        });
    }

    public CompletableFuture<Void> setRole(int guildId, UUID uuid, GuildRole role) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE guild_members SET role = ? WHERE guild_id = ? AND uuid = ?")) {
                statement.setString(1, role.name());
                statement.setInt(2, guildId);
                statement.setString(3, uuid.toString());
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to set role for " + uuid + " in guild " + guildId, ex);
            }
            return null;
        });
    }

    public CompletableFuture<Void> setHome(int guildId, Location location) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE guilds SET home_world = ?, home_x = ?, home_y = ?, home_z = ?, home_yaw = ?, home_pitch = ? WHERE id = ?")) {
                statement.setString(1, location.getWorld().getName());
                statement.setDouble(2, location.getX());
                statement.setDouble(3, location.getY());
                statement.setDouble(4, location.getZ());
                statement.setFloat(5, location.getYaw());
                statement.setFloat(6, location.getPitch());
                statement.setInt(7, guildId);
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to set home for guild " + guildId, ex);
            }
            return null;
        });
    }

    public CompletableFuture<Boolean> depositBank(int guildId, BigDecimal amount) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement("UPDATE guilds SET balance = balance + ? WHERE id = ?")) {
                statement.setBigDecimal(1, amount);
                statement.setInt(2, guildId);
                return statement.executeUpdate() > 0;
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to deposit to guild bank " + guildId, ex);
            }
        });
    }

    public CompletableFuture<Boolean> withdrawBank(int guildId, BigDecimal amount) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE guilds SET balance = balance - ? WHERE id = ? AND balance >= ?")) {
                statement.setBigDecimal(1, amount);
                statement.setInt(2, guildId);
                statement.setBigDecimal(3, amount);
                return statement.executeUpdate() > 0;
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to withdraw from guild bank " + guildId, ex);
            }
        });
    }

    public CompletableFuture<Optional<Guild>> getGuildByMember(UUID uuid) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection()) {
                Integer guildId = null;
                try (PreparedStatement statement = connection.prepareStatement("SELECT guild_id FROM guild_members WHERE uuid = ?")) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            guildId = resultSet.getInt("guild_id");
                        }
                    }
                }
                if (guildId == null) {
                    return Optional.<Guild>empty();
                }
                return loadGuild(connection, guildId);
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to load guild for member " + uuid, ex);
            }
        });
    }

    public CompletableFuture<Optional<Guild>> getGuildByName(String name) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection()) {
                Integer guildId = null;
                try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM guilds WHERE name = ?")) {
                    statement.setString(1, name);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            guildId = resultSet.getInt("id");
                        }
                    }
                }
                if (guildId == null) {
                    return Optional.<Guild>empty();
                }
                return loadGuild(connection, guildId);
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to load guild '" + name + "'", ex);
            }
        });
    }

    public CompletableFuture<List<Guild>> listGuilds() {
        return plugin.scheduler().supplyAsync(() -> {
            List<Guild> guilds = new ArrayList<>();
            try (Connection connection = database.getConnection()) {
                List<Integer> ids = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM guilds ORDER BY name");
                     ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        ids.add(resultSet.getInt("id"));
                    }
                }
                for (int id : ids) {
                    loadGuild(connection, id).ifPresent(guilds::add);
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to list guilds", ex);
            }
            return guilds;
        });
    }

    private Optional<Guild> loadGuild(Connection connection, int guildId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, name, tag, owner, balance, member_limit, home_world, home_x, home_y, home_z, home_yaw, home_pitch, created_at "
                        + "FROM guilds WHERE id = ?")) {
            statement.setInt(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                List<GuildMember> members = loadMembers(connection, guildId);
                Guild guild = new Guild(
                        resultSet.getInt("id"), resultSet.getString("name"), resultSet.getString("tag"),
                        UUID.fromString(resultSet.getString("owner")), resultSet.getBigDecimal("balance"),
                        resultSet.getInt("member_limit"),
                        resultSet.getString("home_world"),
                        resultSet.getDouble("home_x"), resultSet.getDouble("home_y"), resultSet.getDouble("home_z"),
                        resultSet.getFloat("home_yaw"), resultSet.getFloat("home_pitch"),
                        resultSet.getLong("created_at"),
                        members);
                return Optional.of(guild);
            }
        }
    }

    private List<GuildMember> loadMembers(Connection connection, int guildId) throws SQLException {
        List<GuildMember> members = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT uuid, role, joined_at FROM guild_members WHERE guild_id = ?")) {
            statement.setInt(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(new GuildMember(
                            UUID.fromString(resultSet.getString("uuid")),
                            GuildRole.valueOf(resultSet.getString("role")),
                            resultSet.getLong("joined_at")));
                }
            }
        }
        return members;
    }
}
