package gg.nurmi.tablist;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Header/footer use Paper's native Adventure API - no packets needed there. The "reserved slots"
 * behavior (keeping the tablist visually full-size even when few players are online) is the one
 * part vanilla Bukkit genuinely can't do, so it's the only packet-driven piece here: synthetic,
 * blank-looking filler tab entries with no corresponding entity, only ever added/removed via
 * PacketEvents. Gracefully disables itself (header/footer keep working) if PacketEvents isn't
 * installed.
 */
public final class TablistManager {

    private static final int COLUMNS = 4;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final CanvasSuitePlugin plugin;
    private final List<UUID> fillerIds;
    private int currentFillerCount = 0;

    public TablistManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
        int maxSlots = 20 * COLUMNS;
        this.fillerIds = new ArrayList<>(maxSlots);
        for (int i = 0; i < maxSlots; i++) {
            fillerIds.add(UUID.randomUUID());
        }
    }

    public void refreshHeaderFooter(Player player) {
        Component header = plugin.messages().render(player, "tablist.header");
        Component footer = plugin.messages().render(player, "tablist.footer");
        plugin.scheduler().runAtEntity(player, () -> player.sendPlayerListHeaderAndFooter(header, footer), () -> {});
    }

    public void refreshAllHeaderFooters() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshHeaderFooter(player);
        }
    }

    /**
     * Recomputes how many filler slots are needed and broadcasts only the difference.
     * Synchronized because join/quit events for different players can fire on different region
     * threads concurrently, and this reads-then-writes {@link #currentFillerCount} - without a lock
     * two overlapping calls could race and leave viewers with a slot count that's out of sync with
     * what's actually been sent.
     */
    public synchronized void onPlayerCountChanged() {
        if (!fillerSlotsEnabled()) {
            return;
        }
        int totalSlots = rows() * COLUMNS;
        int online = Bukkit.getOnlinePlayers().size();
        int needed = Math.clamp(totalSlots - online, 0, fillerIds.size());
        if (needed == currentFillerCount) {
            return;
        }

        PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();
        if (needed > currentFillerCount) {
            List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> added = new ArrayList<>();
            for (int i = currentFillerCount; i < needed; i++) {
                added.add(buildFillerInfo(i));
            }
            WrapperPlayServerPlayerInfoUpdate addPacket =
                    new WrapperPlayServerPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER, added);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                playerManager.sendPacket(viewer, addPacket);
            }
        } else {
            List<UUID> removed = new ArrayList<>(fillerIds.subList(needed, currentFillerCount));
            WrapperPlayServerPlayerInfoRemove removePacket = new WrapperPlayServerPlayerInfoRemove(removed);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                playerManager.sendPacket(viewer, removePacket);
            }
        }
        currentFillerCount = needed;
    }

    /** A freshly joined player has never seen the currently active filler entries - introduce them directly. */
    public synchronized void introduceFillersTo(Player joined) {
        if (!fillerSlotsEnabled() || currentFillerCount == 0) {
            return;
        }
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = new ArrayList<>();
        for (int i = 0; i < currentFillerCount; i++) {
            entries.add(buildFillerInfo(i));
        }
        WrapperPlayServerPlayerInfoUpdate packet =
                new WrapperPlayServerPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER, entries);
        PacketEvents.getAPI().getPlayerManager().sendPacket(joined, packet);
    }

    private boolean fillerSlotsEnabled() {
        return plugin.packetEvents().available() && plugin.getConfig().getBoolean("tablist.reserved-slots-enabled", true);
    }

    private int rows() {
        return Math.clamp(plugin.getConfig().getInt("tablist.rows", 20), 1, 20);
    }

    private WrapperPlayServerPlayerInfoUpdate.PlayerInfo buildFillerInfo(int index) {
        UserProfile profile = new UserProfile(fillerIds.get(index), blankName(index));
        return new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile, true, 0, GameMode.SURVIVAL, Component.empty(), null);
    }

    /** Two legacy color codes render as nothing visible and give 256 guaranteed-unique combinations. */
    private String blankName(int index) {
        return "§" + HEX[(index / 16) % 16] + "§" + HEX[index % 16];
    }
}
