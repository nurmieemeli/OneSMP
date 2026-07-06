package gg.nurmi.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Thread-safe per-player cooldown tracker (safe to read/write from any region thread). */
public final class Cooldown {

    private final ConcurrentHashMap<UUID, Long> expiries = new ConcurrentHashMap<>();

    public boolean isOnCooldown(UUID id) {
        Long expiry = expiries.get(id);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    public long remainingSeconds(UUID id) {
        Long expiry = expiries.get(id);
        if (expiry == null) {
            return 0;
        }
        return Math.max(0, TimeUnit.MILLISECONDS.toSeconds(expiry - System.currentTimeMillis()));
    }

    public void set(UUID id, long seconds) {
        expiries.put(id, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds));
    }

    public void clear(UUID id) {
        expiries.remove(id);
    }
}
