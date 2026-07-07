package gg.nurmi.message;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks which online staff currently have private messages mirrored to them. */
public final class SocialSpyToggle {

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(UUID uuid) {
        return enabled.contains(uuid);
    }

    /** Flips the toggle and returns the new state. */
    public boolean toggle(UUID uuid) {
        if (!enabled.remove(uuid)) {
            enabled.add(uuid);
            return true;
        }
        return false;
    }

    public Set<UUID> enabled() {
        return enabled;
    }
}
