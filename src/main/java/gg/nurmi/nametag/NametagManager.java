package gg.nurmi.nametag;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.CollisionRule;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.NameTagVisibility;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.OptionData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode;
import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Overhead nametag prefixes, sent purely as per-player scoreboard team packets (never a real
 * Bukkit Scoreboard/Team object) via PacketEvents. Every online player gets their own team, one
 * viewer-facing packet at a time, so a prefix change for one player never touches anyone else's
 * team assignment.
 *
 * <p>There's no LuckPerms hook available here (prefixes are resolved purely through the
 * MiniPlaceholders-LuckPerms expansion, never the LuckPerms API directly), so permission-group
 * changes can only be picked up by periodically re-rendering every online player's prefix — see
 * {@link #refreshAll()}, wired to a repeating task.</p>
 */
public final class NametagManager {

    private final CanvasSuitePlugin plugin;
    private final Map<UUID, Component> lastSent = new ConcurrentHashMap<>();

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
    }

    /** Re-renders one player's prefix and broadcasts it only if it actually changed since last sent. */
    public void refresh(Player player) {
        if (!plugin.packetEvents().available()) {
            return;
        }
        plugin.scheduler().runAtEntity(player, () -> {
            Component prefix = plugin.messages().render(player, "nametag.format");
            Component previous = lastSent.put(player.getUniqueId(), prefix);
            boolean firstTime = previous == null;
            if (!firstTime && previous.equals(prefix)) {
                return;
            }
            WrapperPlayServerTeams packet = buildPacket(player, prefix, firstTime ? TeamMode.CREATE : TeamMode.UPDATE);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                sendPacketTo(viewer, packet);
            }
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
