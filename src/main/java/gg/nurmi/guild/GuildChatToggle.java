package gg.nurmi.guild;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks which online players currently have their normal chat routed to guild-only chat. */
public final class GuildChatToggle {

    private final Set<UUID> toggled = ConcurrentHashMap.newKeySet();

    public boolean isToggled(UUID uuid) {
        return toggled.contains(uuid);
    }

    /** Flips the toggle and returns the new state. */
    public boolean toggle(UUID uuid) {
        if (!toggled.remove(uuid)) {
            toggled.add(uuid);
            return true;
        }
        return false;
    }

    public void clear(UUID uuid) {
        toggled.remove(uuid);
    }
}
