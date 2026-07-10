package gg.nurmi.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionCooldown {

    private final Map<UUID, Map<String, Long>> lastActionMillis = new ConcurrentHashMap<>();

    // Silently rejects (no message/sound) repeat calls within cooldownMillis of the last one per player+category.
    public boolean tryAcquire(UUID uuid, String category, long cooldownMillis) {
        long now = System.currentTimeMillis();
        Long previous = lastActionMillis.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>()).put(category, now);
        return previous == null || now - previous >= cooldownMillis;
    }

    public void clear(UUID uuid) {
        lastActionMillis.remove(uuid);
    }
}
