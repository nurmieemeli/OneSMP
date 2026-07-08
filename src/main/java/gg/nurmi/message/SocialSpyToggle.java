package gg.nurmi.message;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SocialSpyToggle {

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(UUID uuid) {
        return enabled.contains(uuid);
    }

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
