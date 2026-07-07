package gg.nurmi.moderation;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes a moderator truly invisible by intercepting outgoing packets that reference them - not
 * Bukkit's Player#hidePlayer/showPlayer API. Hiding proactively strips existing client-side state
 * (tab list entry + entity) and then blocks any future packet about the hidden player from being
 * sent to anyone except themselves; showing removes the block and manually rebuilds the minimum
 * packets needed for everyone else's client to see them again.
 *
 * <p>The rebuilt spawn-entity/tab-entry on {@link #show(Player)} is intentionally minimal (no skin
 * texture properties, equipment, or pose state) - good enough for the moderator to reappear, but a
 * full-fidelity re-sync is what Bukkit's own entity tracker would normally provide on next tick.</p>
 */
public final class PacketVanishController {

    private final CanvasSuitePlugin plugin;
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

    public PacketVanishController(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
        if (!plugin.packetEvents().available()) {
            return;
        }
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                handlePacketSend(event);
            }
        });
    }

    private void handlePacketSend(PacketSendEvent event) {
        if (hidden.isEmpty()) {
            return;
        }
        Object receiverObj = event.getPlayer();
        if (!(receiverObj instanceof Player receiver)) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(event);
            List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> filtered = new ArrayList<>(packet.getEntries());
            boolean changed = filtered.removeIf(entry ->
                    hidden.contains(entry.getProfileId()) && !entry.getProfileId().equals(receiver.getUniqueId()));
            if (changed) {
                packet.setEntries(filtered);
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            Optional<UUID> uuid = packet.getUUID();
            if (uuid.isPresent() && hidden.contains(uuid.get()) && !uuid.get().equals(receiver.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    public void hide(Player moderator) {
        if (!plugin.packetEvents().available()) {
            return;
        }
        hidden.add(moderator.getUniqueId());

        PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();
        WrapperPlayServerPlayerInfoRemove removeInfo = new WrapperPlayServerPlayerInfoRemove(moderator.getUniqueId());
        WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(moderator.getEntityId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(moderator)) {
                continue;
            }
            playerManager.sendPacket(viewer, removeInfo);
            playerManager.sendPacket(viewer, destroy);
        }
    }

    public void show(Player moderator) {
        if (!plugin.packetEvents().available()) {
            return;
        }
        hidden.remove(moderator.getUniqueId());

        PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();
        UserProfile profile = new UserProfile(moderator.getUniqueId(), moderator.getName());
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile, true, 0, GameMode.SURVIVAL, null, null);
        WrapperPlayServerPlayerInfoUpdate addInfo =
                new WrapperPlayServerPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER, info);

        Location location = moderator.getLocation();
        Vector3d position = new Vector3d(location.getX(), location.getY(), location.getZ());
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(moderator.getEntityId(),
                Optional.of(moderator.getUniqueId()), EntityTypes.PLAYER, position,
                location.getPitch(), location.getYaw(), location.getYaw(), 0, Optional.empty());

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(moderator)) {
                continue;
            }
            playerManager.sendPacket(viewer, addInfo);
            playerManager.sendPacket(viewer, spawn);
        }
    }
}
