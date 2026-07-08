package gg.nurmi.message;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.storage.Database;
import gg.nurmi.util.Cooldown;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PrivateMessageManager {

    private final CanvasSuitePlugin plugin;
    private final Database database;
    private final SocialSpyToggle socialSpy;
    private final Cooldown cooldown = new Cooldown();
    private final Map<UUID, UUID> lastConversant = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> ignoreCache = new ConcurrentHashMap<>();

    public PrivateMessageManager(CanvasSuitePlugin plugin, SocialSpyToggle socialSpy) {
        this.plugin = plugin;
        this.database = plugin.database();
        this.socialSpy = socialSpy;
    }

    public void handleJoin(UUID uuid) {
        plugin.scheduler().runAsync(() -> {
            Set<UUID> ignored = ConcurrentHashMap.newKeySet();
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT ignored FROM ignored_players WHERE owner = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        ignored.add(UUID.fromString(resultSet.getString("ignored")));
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load ignore list for " + uuid, ex);
            }
            ignoreCache.put(uuid, ignored);
        });
    }

    public void handleQuit(UUID uuid) {
        ignoreCache.remove(uuid);
    }

    public boolean isIgnoring(UUID owner, UUID target) {
        Set<UUID> ignored = ignoreCache.get(owner);
        return ignored != null && ignored.contains(target);
    }

    public boolean toggleIgnore(UUID owner, UUID target) {
        Set<UUID> ignored = ignoreCache.computeIfAbsent(owner, id -> ConcurrentHashMap.newKeySet());
        boolean nowIgnoring = ignored.add(target);
        if (!nowIgnoring) {
            ignored.remove(target);
        }
        persistIgnore(owner, target, nowIgnoring);
        return nowIgnoring;
    }

    private void persistIgnore(UUID owner, UUID target, boolean nowIgnoring) {
        plugin.scheduler().runAsync(() -> {
            String sql = nowIgnoring
                    ? "INSERT INTO ignored_players(owner, ignored) VALUES (?, ?)"
                    : "DELETE FROM ignored_players WHERE owner = ? AND ignored = ?";
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, owner.toString());
                statement.setString(2, target.toString());
                statement.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to persist ignore state for " + owner, ex);
            }
        });
    }

    public UUID lastConversant(UUID uuid) {
        return lastConversant.get(uuid);
    }

    public void sendMessage(Player sender, Player target, String rawMessage) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            plugin.messages().send(sender, "msg.self");
            return;
        }

        int cooldownSeconds = plugin.getConfig().getInt("msg.cooldown-seconds", 0);
        if (cooldownSeconds > 0 && cooldown.isOnCooldown(sender.getUniqueId())) {
            plugin.messages().send(sender, "msg.cooldown",
                    Placeholder.unparsed("seconds", String.valueOf(cooldown.remainingSeconds(sender.getUniqueId()))));
            return;
        }

        if (isIgnoring(target.getUniqueId(), sender.getUniqueId())) {
            plugin.messages().send(sender, "msg.blocked");
            return;
        }

        Component messageComponent = sender.hasPermission("canvassuite.chat.format")
                ? plugin.messages().parse(rawMessage)
                : Component.text(rawMessage);

        if (cooldownSeconds > 0) {
            cooldown.set(sender.getUniqueId(), cooldownSeconds);
        }
        lastConversant.put(sender.getUniqueId(), target.getUniqueId());
        lastConversant.put(target.getUniqueId(), sender.getUniqueId());

        plugin.messages().send(sender, "msg.sent",
                Placeholder.unparsed("target", target.getName()), Placeholder.component("message", messageComponent));
        plugin.messages().send(target, "msg.received",
                Placeholder.unparsed("sender", sender.getName()), Placeholder.component("message", messageComponent));

        for (UUID spyId : socialSpy.enabled()) {
            if (spyId.equals(sender.getUniqueId()) || spyId.equals(target.getUniqueId())) {
                continue;
            }
            Player spy = Bukkit.getPlayer(spyId);
            if (spy != null) {
                plugin.messages().send(spy, "msg.socialspy",
                        Placeholder.unparsed("sender", sender.getName()),
                        Placeholder.unparsed("target", target.getName()),
                        Placeholder.component("message", messageComponent));
            }
        }
    }
}
