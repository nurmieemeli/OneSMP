package gg.nurmi.nametag;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.CollisionRule;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.NameTagVisibility;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.OptionData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode;
import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.guild.Guild;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class NametagManager {

    private final CanvasSuitePlugin plugin;
    private final Map<UUID, Component> lastSent = new ConcurrentHashMap<>();
    private record GuildTagState(int entityId, UUID displayUuid, Component tag, String world) {}

    private final Map<UUID, GuildTagState> guildTagState = new ConcurrentHashMap<>();
    private final AtomicInteger fakeEntityIdCounter = new AtomicInteger(2_000_000_000);

    public NametagManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public void handleJoin(Player joined) {
        if (!plugin.packetEvents().available()) {
            return;
        }
        refresh(joined);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(joined)) {
                continue;
            }
            Component prefix = lastSent.get(other.getUniqueId());
            if (prefix != null) {
                sendPacketTo(joined, buildPacket(other, prefix, TeamMode.CREATE));
            }

            GuildTagState state = guildTagState.get(other.getUniqueId());
            if (state != null) {
                plugin.scheduler().runAtEntity(other,
                        () -> introduceGuildTagEntity(joined, state.entityId(), state.displayUuid(), other.getLocation(),
                                other.getEntityId(), state.tag()),
                        () -> {});
            }
        }
    }

    public void handleQuit(Player left) {
        if (!plugin.packetEvents().available()) {
            return;
        }
        lastSent.remove(left.getUniqueId());
        WrapperPlayServerTeams removePacket = new WrapperPlayServerTeams(teamName(left), TeamMode.REMOVE, (ScoreBoardTeamInfo) null, List.of());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(left)) {
                sendPacketTo(viewer, removePacket);
            }
        }

        GuildTagState state = guildTagState.remove(left.getUniqueId());
        if (state != null) {
            destroyGuildTagEntity(state.entityId());
        }
    }

    public void handleWorldChange(Player player) {
        if (!plugin.packetEvents().available()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        GuildTagState old = guildTagState.get(uuid);
        if (old == null) {
            return;
        }

        plugin.scheduler().runAtEntity(player, () -> {
            destroyGuildTagEntity(old.entityId());
            int newId = fakeEntityIdCounter.decrementAndGet();
            UUID displayUuid = UUID.randomUUID();
            Location location = player.getLocation();
            guildTagState.put(uuid, new GuildTagState(newId, displayUuid, old.tag(), location.getWorld().getName()));
            spawnGuildTagEntity(newId, displayUuid, player, location, old.tag());

            plugin.scheduler().runAtEntityDelayed(player, () -> sendMount(player, newId), () -> {}, 5L);
        }, () -> {});
    }

    public void refresh(Player player) {
        if (!plugin.packetEvents().available()) {
            return;
        }
        plugin.scheduler().runAtEntity(player, () -> {
            Component prefix = plugin.messages().render(player, "nametag.format");
            Component previous = lastSent.put(player.getUniqueId(), prefix);
            boolean firstTime = previous == null;
            if (firstTime || !previous.equals(prefix)) {
                WrapperPlayServerTeams packet = buildPacket(player, prefix, firstTime ? TeamMode.CREATE : TeamMode.UPDATE);
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    sendPacketTo(viewer, packet);
                }
            }

            plugin.guilds().getGuildByMember(player.getUniqueId()).thenAccept(optionalGuild -> {
                String tag = optionalGuild.map(Guild::tag).orElse(null);
                plugin.scheduler().runAtEntity(player, () -> applyGuildTag(player, tag), () -> {});
            });
        }, () -> {});
    }

    public void refreshAll() {
        if (!plugin.packetEvents().available()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void reassertMounts() {
        if (!plugin.packetEvents().available() || guildTagState.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, GuildTagState> entry : guildTagState.entrySet()) {
            try {
                Player owner = Bukkit.getPlayer(entry.getKey());
                if (owner != null) {
                    sendMount(owner, entry.getValue().entityId());
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to reassert guild-tag mount for " + entry.getKey(), ex);
            }
        }
    }

    private void sendMount(Player owner, int fakeEntityId) {
        WrapperPlayServerSetPassengers mount = new WrapperPlayServerSetPassengers(owner.getEntityId(), new int[]{fakeEntityId});
        PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            playerManager.sendPacket(viewer, mount);
        }
    }

    private void applyGuildTag(Player player, String tag) {
        UUID uuid = player.getUniqueId();

        if (tag == null) {
            GuildTagState removed = guildTagState.remove(uuid);
            if (removed != null) {
                destroyGuildTagEntity(removed.entityId());
            }
            return;
        }

        String format = plugin.getConfig().getString("nametag.guild-tag.format", "<gray>[<tag>]");
        Component rendered = plugin.messages().parse(format, player, Placeholder.unparsed("tag", tag));
        String currentWorld = player.getWorld().getName();

        GuildTagState existing = guildTagState.get(uuid);
        if (existing == null || !existing.world().equals(currentWorld)) {
            if (existing != null) {
                destroyGuildTagEntity(existing.entityId());
            }
            int newId = fakeEntityIdCounter.decrementAndGet();
            UUID displayUuid = UUID.randomUUID();
            guildTagState.put(uuid, new GuildTagState(newId, displayUuid, rendered, currentWorld));
            spawnGuildTagEntity(newId, displayUuid, player, player.getLocation(), rendered);
            return;
        }

        if (!rendered.equals(existing.tag())) {
            guildTagState.put(uuid, new GuildTagState(existing.entityId(), existing.displayUuid(), rendered, existing.world()));
            sendTextUpdate(existing.entityId(), rendered);
        }
    }

    private void spawnGuildTagEntity(int entityId, UUID displayUuid, Player owner, Location location, Component text) {
        PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();
        WrapperPlayServerSpawnEntity spawn = buildSpawnPacket(entityId, displayUuid, location);
        WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(entityId, buildMetadata(text));
        WrapperPlayServerSetPassengers mount = new WrapperPlayServerSetPassengers(owner.getEntityId(), new int[]{entityId});

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            playerManager.sendPacket(viewer, spawn);
            playerManager.sendPacket(viewer, metaPacket);
            playerManager.sendPacket(viewer, mount);
        }
    }

    private void introduceGuildTagEntity(Player viewer, int entityId, UUID displayUuid, Location location,
                                          int ownerEntityId, Component text) {
        PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();
        playerManager.sendPacket(viewer, buildSpawnPacket(entityId, displayUuid, location));
        playerManager.sendPacket(viewer, new WrapperPlayServerEntityMetadata(entityId, buildMetadata(text)));
        playerManager.sendPacket(viewer, new WrapperPlayServerSetPassengers(ownerEntityId, new int[]{entityId}));
    }

    private WrapperPlayServerSpawnEntity buildSpawnPacket(int entityId, UUID displayUuid, Location location) {
        Vector3d position = new Vector3d(location.getX(), location.getY(), location.getZ());
        return new WrapperPlayServerSpawnEntity(entityId, Optional.of(displayUuid),
                EntityTypes.TEXT_DISPLAY, position, 0f, 0f, 0f, 0, Optional.empty());
    }

    private List<EntityData<?>> buildMetadata(Component text) {
        List<EntityData<?>> list = new ArrayList<>();
        list.add(new EntityData<>(5, EntityDataTypes.BOOLEAN, true));
        float yOffset = (float) plugin.getConfig().getDouble("nametag.guild-tag.y-offset", 0.55);
        list.add(new EntityData<>(11, EntityDataTypes.VECTOR3F, new Vector3f(0f, yOffset, 0f)));
        list.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) 3));
        list.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, text));
        return list;
    }

    private void sendTextUpdate(int entityId, Component text) {
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, text));
        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(entityId, metadata);
        PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            playerManager.sendPacket(viewer, packet);
        }
    }

    private void destroyGuildTagEntity(int entityId) {
        WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(entityId);
        PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            playerManager.sendPacket(viewer, destroy);
        }
    }

    private WrapperPlayServerTeams buildPacket(Player subject, Component prefix, TeamMode mode) {
        ScoreBoardTeamInfo info = new ScoreBoardTeamInfo(Component.empty(), prefix, Component.empty(),
                NameTagVisibility.ALWAYS, CollisionRule.ALWAYS, trailingColor(prefix, NamedTextColor.WHITE), OptionData.NONE);
        List<String> members = mode == TeamMode.CREATE ? List.of(subject.getName()) : List.of();
        return new WrapperPlayServerTeams(teamName(subject), mode, info, members);
    }

    /**
     * The vanilla team system renders "prefix + real name + suffix", but the name portion is only
     * ever colorable via this single legacy team color field - never full Component styling. To
     * make a prefix's color still carry onto the name (matching how nesting Components normally
     * cascades), this walks down the prefix's last child at each level to find whatever color
     * would be "active" right after the prefix ends, the same as if the name were appended as a
     * nested child instead of being a separate field.
     */
    private NamedTextColor trailingColor(Component component, NamedTextColor inherited) {
        TextColor own = component.color();
        NamedTextColor current = own != null ? NamedTextColor.nearestTo(own) : inherited;
        List<Component> children = component.children();
        return children.isEmpty() ? current : trailingColor(children.get(children.size() - 1), current);
    }

    private void sendPacketTo(Player viewer, WrapperPlayServerTeams packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private String teamName(Player player) {
        return "csnt_" + player.getUniqueId().toString().replace("-", "").substring(0, 11);
    }
}
