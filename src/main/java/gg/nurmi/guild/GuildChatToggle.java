package gg.nurmi.guild;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuildChatToggle {

    private final Set<UUID> toggled = ConcurrentHashMap.newKeySet();

    public boolean isToggled(UUID uuid) {
        return toggled.contains(uuid);
    }

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
