package gg.nurmi.teleport;

import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory, expiring /tpa request tracking — keyed by the target (the player who must accept/deny). */
public final class TpaManager {

    public record TpaRequest(UUID requester, UUID target, boolean here, long expiresAtMillis) {
    }

    private final CanvasSuitePlugin plugin;
    private final Map<UUID, TpaRequest> incomingByTarget = new ConcurrentHashMap<>();

    public TpaManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    public boolean hasPendingTo(UUID requester, UUID target) {
        TpaRequest request = incomingByTarget.get(target);
        return request != null && request.requester().equals(requester) && !isExpired(request);
    }

    public void createRequest(UUID requester, UUID target, boolean here) {
        long timeoutSeconds = plugin.getConfig().getLong("teleport.tpa-request-timeout-seconds", 60);
        incomingByTarget.put(target, new TpaRequest(requester, target, here, System.currentTimeMillis() + timeoutSeconds * 1000));
    }

    public Optional<TpaRequest> pendingFor(UUID target) {
        TpaRequest request = incomingByTarget.get(target);
        if (request == null || isExpired(request)) {
            incomingByTarget.remove(target);
            return Optional.empty();
        }
        return Optional.of(request);
    }

    public void clear(UUID target) {
        incomingByTarget.remove(target);
    }

    private boolean isExpired(TpaRequest request) {
        return request.expiresAtMillis() < System.currentTimeMillis();
    }

    private void startCleanupTask() {
        plugin.scheduler().runAsyncRepeating(() -> {
            Iterator<Map.Entry<UUID, TpaRequest>> iterator = incomingByTarget.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, TpaRequest> entry = iterator.next();
                if (!isExpired(entry.getValue())) {
                    continue;
                }
                iterator.remove();
                Player requester = Bukkit.getPlayer(entry.getValue().requester());
                if (requester != null) {
                    Player target = Bukkit.getPlayer(entry.getKey());
                    plugin.messages().send(requester, "teleport.tpa-expired",
                            Placeholder.unparsed("target", target != null ? target.getName() : "?"));
                }
            }
        }, 5000, 5000);
    }
}
