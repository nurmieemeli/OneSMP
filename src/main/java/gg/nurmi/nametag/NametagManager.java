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

/**
 * Overhead nametags, entirely packet-driven (no real Bukkit Scoreboard/Team or entity is ever
 * registered):
 * <ul>
 *   <li>Line 1 (the prefix in front of the player's real name) is a per-player scoreboard team
 *   packet, same as before.</li>
 *   <li>Line 2 (the guild tag, only present while the player is in a guild) is a fake TEXT_DISPLAY
 *   entity mounted as a passenger of the player - the same technique plugins like
 *   <a href="https://github.com/alexdev03/UnlimitedNametags">UnlimitedNametags</a> use, since
 *   vanilla team prefixes/suffixes can only add text to the *same* line as a player's name, never
 *   a second stacked line. Riding as a passenger means the client keeps it positioned correctly on
 *   its own with zero ongoing position-packet traffic.</li>
 * </ul>
 *
 * <p><b>Why the mount kept appearing to "fail" for other viewers:</b> {@code SetPassengers}
 * replaces an entity's <i>entire</i> passenger list rather than adding to it, and the real server
 * sends its own {@code SetPassengers} packet for the subject whenever they become newly visible to
 * a viewer (ordinary entity-tracking, e.g. on join) - reflecting the subject's real (empty, since
 * our fake entity was never a real passenger) passenger list. That packet can race ours and
 * silently clear the mount right after we set it. Continuously re-teleporting the entity's
 * position works around this but is expensive; instead, {@link #reassertMounts()} periodically
 * re-sends just the (tiny) mount packet to win back any mount vanilla cleared - a fraction of the
 * cost of tracking position, since the client still does the actual per-frame following for free
 * once the mount sticks.</p>
 *
 * <p>There's no LuckPerms hook available here (prefixes are resolved purely through the
 * MiniPlaceholders-LuckPerms expansion, never the LuckPerms API directly), so permission-group
 * changes can only be picked up by periodically re-rendering every online player's prefix — see
 * {@link #refreshAll()}, wired to a repeating task. The same refresh cycle also re-checks guild
 * membership.</p>
 */
public final class NametagManager {

    private final CanvasSuitePlugin plugin;
    private final Map<UUID, Component> lastSent = new ConcurrentHashMap<>();
    /**
     * Entity id + display UUID + last-rendered tag + the world it was last spawned into, for a
     * player's guild-tag entity, kept as one unit so readers (handleJoin, reassertMounts) always
     * see a consistent state - splitting these across separate maps let a viewer be introduced to
     * an entity id that a concurrent handleQuit/handleWorldChange had already destroyed. The world
     * is tracked so the periodic refresh cycle (applyGuildTag) can self-heal a world change even if
     * handleWorldChange's own dedicated, immediate respawn didn't stick for whatever reason -
     * without it, "same tag text as before" alone would wrongly look like nothing needs to be
     * resent, even though the entity was actually left behind in the old world.
     */
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
                // Reading another player's live location/entity id must happen on their own entity thread.
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

    /**
     * A world change fully destroys and recreates the real player entity client-side for viewers
     * (a different world is effectively a different set of regions/viewers entirely) - our fake
     * entity is never told about that, so it's left behind in the old world. Destroy it and spawn
     * a fresh one (new entity ID/UUID, same as any other respawn) at the player's new location.
     */
    public void handleWorldChange(Player player) {
        if (!plugin.packetEvents().available()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        GuildTagState old = guildTagState.get(uuid);
        if (old == null) {
            return;
        }

        // Destroying the old entity and swapping in the new one both happen inside the hop, so if
        // the entity retires before this task runs (ifRetired, a no-op) the map is left untouched -
        // still pointing at the old (never-destroyed) entity - instead of a stale id for one we'd
        // already told every viewer to scrap.
        plugin.scheduler().runAtEntity(player, () -> {
            destroyGuildTagEntity(old.entityId());
            int newId = fakeEntityIdCounter.decrementAndGet();
            UUID displayUuid = UUID.randomUUID();
            Location location = player.getLocation();
            guildTagState.put(uuid, new GuildTagState(newId, displayUuid, old.tag(), location.getWorld().getName()));
            spawnGuildTagEntity(newId, displayUuid, player, location, old.tag());

            // Every viewer in the new world experiences this as a fresh "entity became visible"
            // tracking event for the player, so vanilla's own SetPassengers (see class doc) races
            // ours far more reliably here than during ordinary play. Win that race deterministically
            // with a quick follow-up instead of waiting for the next periodic reassertMounts() tick.
            plugin.scheduler().runAtEntityDelayed(player, () -> sendMount(player, newId), () -> {}, 5L);
        }, () -> {});
    }

    /** Re-renders one player's prefix/guild tag and broadcasts either only if actually changed since last sent. */
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
                // Re-hop rather than trust a Location captured before this async guild lookup
                // started - by the time it completes the player may have changed worlds, and
                // applyGuildTag needs their *current* world to notice that.
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

    /**
     * Re-sends just the (tiny) mount packet for every active guild-tag entity, to win back any
     * mount the real server's own entity-tracking silently cleared - see the class doc for why
     * that happens. Meant to run on a low-frequency repeating task (a couple of times a minute is
     * plenty); this is not a position sync, so it costs nothing proportional to player movement.
     */
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
                // One bad entry must not take down this whole repeating task: Folia's fixed-rate
                // global scheduler does not reschedule a task that threw, so an uncaught exception
                // here would silently and permanently disable the only mechanism that wins guild-tag
                // mounts back from vanilla's own entity-tracking (see class doc) for every player,
                // for the rest of the server's uptime - not just the one entry that failed.
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
            // No entity yet, OR the one we think is alive was actually left behind in a different
            // world - e.g. handleWorldChange's own dedicated respawn didn't stick. Respawn fresh
            // rather than trusting a stale reference no client in this world has ever seen; this is
            // what makes recovery self-healing within one refresh cycle even if that fast path fails.
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

    /** Spawns+mounts the entity for a single newly-joined viewer, who has never seen it before. */
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

    /** Index table per the vanilla Display/TextDisplay entity metadata layout - see Minecraft's protocol docs. */
    private List<EntityData<?>> buildMetadata(Component text) {
        List<EntityData<?>> list = new ArrayList<>();
        list.add(new EntityData<>(5, EntityDataTypes.BOOLEAN, true)); // no gravity
        // While riding, the passenger has no defined seat height (unlike a horse/boat) - this
        // translation is the only thing controlling how far above the mount point it floats.
        float yOffset = (float) plugin.getConfig().getDouble("nametag.guild-tag.y-offset", 0.55);
        list.add(new EntityData<>(11, EntityDataTypes.VECTOR3F, new Vector3f(0f, yOffset, 0f))); // translation
        list.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) 3)); // billboard: CENTER (always faces viewer)
        list.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, text)); // text
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
                NameTagVisibility.ALWAYS, CollisionRule.ALWAYS, NamedTextColor.WHITE, OptionData.NONE);
        List<String> members = mode == TeamMode.CREATE ? List.of(subject.getName()) : List.of();
        return new WrapperPlayServerTeams(teamName(subject), mode, info, members);
    }

    private void sendPacketTo(Player viewer, WrapperPlayServerTeams packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private String teamName(Player player) {
        // Team name cap is only 16 chars pre-1.18; stay conservative for older clients.
        return "csnt_" + player.getUniqueId().toString().replace("-", "").substring(0, 11);
    }
}
