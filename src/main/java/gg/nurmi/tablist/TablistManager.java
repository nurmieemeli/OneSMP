package gg.nurmi.tablist;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action;
import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

// Maintains a fixed-size grid of fake tablist entries (via PacketEvents) so the tablist never shrinks: real players are mirrored into slots sorted by LuckPerms weight, and every unused slot is backfilled with an invisible filler entry.
public final class TablistManager {

    private static final int COLUMNS = 4;
    private static final int MAX_SLOTS = 20 * COLUMNS;
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final EnumSet<Action> SLOT_ACTIONS = EnumSet.of(
            Action.ADD_PLAYER, Action.UPDATE_LISTED, Action.UPDATE_GAME_MODE,
            Action.UPDATE_DISPLAY_NAME, Action.UPDATE_LIST_ORDER);
    private static final EnumSet<Action> LISTED_ACTIONS = EnumSet.of(Action.UPDATE_LISTED);
    private final CanvasSuitePlugin plugin;
    private final List<UUID> slotIds;
    private final List<TextureProperty> fillerSkin;
    private final AtomicLong generation = new AtomicLong();
    private final UUID[] slotOccupant = new UUID[MAX_SLOTS];
    private final Object layoutLock = new Object();

    public TablistManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
        this.slotIds = new ArrayList<>(MAX_SLOTS);
        for (int i = 0; i < MAX_SLOTS; i++) {
            slotIds.add(UUID.randomUUID());
        }
        this.fillerSkin = loadFillerSkin();
    }

    private List<TextureProperty> loadFillerSkin() {
        String texture = plugin.getConfig().getString("tablist.reserved-slot-skin.texture", "");
        String signature = plugin.getConfig().getString("tablist.reserved-slot-skin.signature", "");
        if (texture.isBlank() || signature.isBlank()) {
            return List.of();
        }
        return List.of(new TextureProperty("textures", texture, signature));
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

    public void refreshLayout() {
        if (!fillerSlotsEnabled()) {
            return;
        }
        long myGeneration = generation.incrementAndGet();
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());

        List<CompletableFuture<PlayerSnapshot>> futures = new ArrayList<>(players.size());
        for (Player player : players) {
            CompletableFuture<PlayerSnapshot> future = new CompletableFuture<>();
            plugin.scheduler().runAtEntity(player,
                    () -> future.complete(PlayerSnapshot.of(plugin, player)),
                    () -> future.complete(null));
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            if (generation.get() != myGeneration) return; // a newer refresh started while this one was gathering snapshots
            List<PlayerSnapshot> snapshots = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(PlayerSnapshot::weight).reversed()
                            .thenComparing(PlayerSnapshot::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            broadcastLayout(snapshots);
        });
    }

    // Only remove/re-add packets are sent for slots whose occupant actually changed, to keep this cheap when the roster is mostly stable.
    private void broadcastLayout(List<PlayerSnapshot> players) {
        int totalSlots = rows() * COLUMNS;
        boolean overflow = players.size() > totalSlots;
        int mirrored = overflow ? totalSlots : players.size();
        int overflowCount = overflow ? players.size() - mirrored : 0;

        PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();

        synchronized (layoutLock) {
            List<UUID> staleSlots = new ArrayList<>();
            for (int k = 0; k < MAX_SLOTS; k++) {
                UUID newOccupant = k < mirrored ? players.get(k).uuid() : null;
                if (k >= totalSlots) newOccupant = null;
                if (!Objects.equals(slotOccupant[k], newOccupant)) {
                    staleSlots.add(slotIds.get(k));
                    slotOccupant[k] = newOccupant;
                }
            }
            if (!staleSlots.isEmpty()) {
                WrapperPlayServerPlayerInfoRemove removePacket = new WrapperPlayServerPlayerInfoRemove(staleSlots);
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    playerManager.sendPacket(viewer, removePacket);
                }
            }

            List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> slots = new ArrayList<>(totalSlots);
            for (int k = 0; k < totalSlots; k++) {
                int listOrder = listOrderFor(k);
                if (k < mirrored) {
                    slots.add(buildMirrorInfo(slotIds.get(k), players.get(k), listOrder));
                } else if (overflow && k == mirrored) {
                    slots.add(buildOverflowInfo(slotIds.get(k), overflowCount, listOrder));
                } else {
                    slots.add(buildFillerInfo(slotIds.get(k), k, listOrder));
                }
            }
            WrapperPlayServerPlayerInfoUpdate slotsPacket = new WrapperPlayServerPlayerInfoUpdate(SLOT_ACTIONS, slots);
            List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> visibility = new ArrayList<>(players.size());
            for (PlayerSnapshot player : players) {
                visibility.add(listedOnlyInfo(player.uuid(), false));
            }
            WrapperPlayServerPlayerInfoUpdate visibilityPacket = new WrapperPlayServerPlayerInfoUpdate(LISTED_ACTIONS, visibility);

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                playerManager.sendPacket(viewer, slotsPacket);
                playerManager.sendPacket(viewer, visibilityPacket);
            }
        }
    }

    public void shutdown() {
        if (!plugin.packetEvents().available()) {
            return;
        }
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> visibility = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            visibility.add(listedOnlyInfo(player.getUniqueId(), true));
        }
        if (visibility.isEmpty()) {
            return;
        }
        WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(LISTED_ACTIONS, visibility);
        PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            playerManager.sendPacket(viewer, packet);
        }
    }

    private boolean fillerSlotsEnabled() {
        return plugin.packetEvents().available() && plugin.getConfig().getBoolean("tablist.reserved-slots-enabled", true);
    }

    private int rows() {
        return Math.clamp(plugin.getConfig().getInt("tablist.rows", 20), 1, 20);
    }

    // Ranks are computed row-major (fill row 0 left-to-right, then row 1, ...) but vanilla's list-order lays slots out column-major, so this remaps one to the other.
    private int listOrderFor(int rowMajorRank) {
        int row = rowMajorRank / COLUMNS;
        int column = rowMajorRank % COLUMNS;
        int columnMajorRank = column * rows() + row;
        return Integer.MAX_VALUE - columnMajorRank;
    }

    private WrapperPlayServerPlayerInfoUpdate.PlayerInfo buildMirrorInfo(UUID slotId, PlayerSnapshot player, int listOrder) {
        UserProfile profile = new UserProfile(slotId, player.name(), player.textures());
        return new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile, true, player.ping(), player.gameMode(), player.displayName(), null, listOrder);
    }

    private WrapperPlayServerPlayerInfoUpdate.PlayerInfo buildFillerInfo(UUID slotId, int index, int listOrder) {
        UserProfile profile = new UserProfile(slotId, blankName(index), fillerSkin);
        return new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile, true, 0, GameMode.SURVIVAL, Component.empty(), null, listOrder);
    }

    private WrapperPlayServerPlayerInfoUpdate.PlayerInfo buildOverflowInfo(UUID slotId, int overflowCount, int listOrder) {
        UserProfile profile = new UserProfile(slotId, "", fillerSkin);
        Component displayName = plugin.messages().render(Audience.empty(), "tablist.overflow",
                Placeholder.unparsed("count", String.valueOf(overflowCount)));
        return new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile, true, 0, GameMode.SURVIVAL, displayName, null, listOrder);
    }

    private WrapperPlayServerPlayerInfoUpdate.PlayerInfo listedOnlyInfo(UUID uuid, boolean listed) {
        return new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                new UserProfile(uuid, ""), listed, 0, GameMode.SURVIVAL, null, null, 0);
    }

    // Vanilla requires each tablist entry to have a unique name; two invisible color codes give MAX_SLOTS distinct blank-looking names.
    private String blankName(int index) {
        return "§" + HEX[(index / 16) % 16] + "§" + HEX[index % 16];
    }

    private record PlayerSnapshot(UUID uuid, String name, int ping, GameMode gameMode, List<TextureProperty> textures,
                                  Component displayName, int weight) {

        static PlayerSnapshot of(CanvasSuitePlugin plugin, Player player) {
            PlayerProfile profile = player.getPlayerProfile();
            List<TextureProperty> textures = new ArrayList<>();
            for (ProfileProperty property : profile.getProperties()) {
                textures.add(new TextureProperty(property.getName(), property.getValue(), property.getSignature()));
            }
            GameMode gameMode = GameMode.valueOf(player.getGameMode().name());
            Component displayName = plugin.messages().render(player, "tablist.name-format");
            return new PlayerSnapshot(player.getUniqueId(), player.getName(), player.getPing(), gameMode, textures,
                    displayName, weightOf(player));
        }

        private static int weightOf(Player player) {
            try {
                return LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player).getWeight();
            } catch (IllegalStateException ex) {
                return 0;
            }
        }
    }
}
