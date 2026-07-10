package gg.nurmi.guild.gui;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.guild.Guild;
import gg.nurmi.guild.GuildManager;
import gg.nurmi.guild.GuildMember;
import gg.nurmi.guild.GuildRole;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public final class GuildMainGui extends AbstractGui {

    private static final int INFO_SLOT = 4;
    private static final int MEMBERS_SLOT = 10;
    private static final int HOME_SLOT = 12;
    private static final int CHAT_TOGGLE_SLOT = 14;
    private static final int SET_HOME_SLOT = 16;
    private static final int DISBAND_OR_LEAVE_SLOT = 20;
    private static final int CLOSE_SLOT = 22;

    public GuildMainGui(OneSMPPlugin plugin, GuildManager guildManager, Guild guild, UUID viewerUuid) {
        super(plugin, plugin.messages().text("guild.gui-main-title",
                Placeholder.unparsed("guild_name", guild.name()), Placeholder.unparsed("guild_tag", guild.tag())), 3);

        GuildRole viewerRole = guild.member(viewerUuid).map(GuildMember::role).orElse(GuildRole.MEMBER);
        String ownerName = Optional.ofNullable(Bukkit.getOfflinePlayer(guild.owner()).getName()).orElse(guild.owner().toString());

        ItemStack info = new ItemBuilder(Material.SHIELD)
                .name(plugin.messages().text("guild.gui-info-button"))
                .lore(
                        plugin.messages().text("guild.gui-owner-lore", Placeholder.unparsed("owner", ownerName)),
                        plugin.messages().text("guild.gui-members-lore",
                                Placeholder.unparsed("count", String.valueOf(guild.members().size())),
                                Placeholder.unparsed("limit", String.valueOf(guild.memberLimit()))),
                        plugin.messages().text("guild.gui-bank-lore", Placeholder.unparsed("balance", plugin.economy().format(guild.balance()))))
                .glow(true)
                .build();
        setItem(INFO_SLOT, info);

        ItemStack membersIcon = new ItemBuilder(Material.PLAYER_HEAD)
                .name(plugin.messages().text("guild.gui-members-button"))
                .lore(plugin.messages().text("guild.gui-members-lore",
                        Placeholder.unparsed("count", String.valueOf(guild.members().size())),
                        Placeholder.unparsed("limit", String.valueOf(guild.memberLimit()))))
                .build();
        setButton(MEMBERS_SLOT, membersIcon, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                new GuildMembersGui(plugin, guildManager, guild, viewerUuid).open(player);
            }
        });

        if (guild.hasHome()) {
            ItemStack homeIcon = new ItemBuilder(Material.RED_BED)
                    .name(plugin.messages().text("guild.gui-home-button"))
                    .lore(plugin.messages().text("gui.click-to-teleport"))
                    .build();
            setButton(HOME_SLOT, homeIcon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    plugin.teleportExecutor().executeSafely(player, guild.homeLocation());
                }
            });
        } else {
            ItemStack lockedHome = new ItemBuilder(Material.GRAY_DYE)
                    .name(plugin.messages().text("guild.gui-home-locked-name"))
                    .lore(plugin.messages().text("guild.gui-home-not-set-lore"))
                    .build();
            setItem(HOME_SLOT, lockedHome);
        }

        boolean chatToggled = plugin.guildChat().isToggled(viewerUuid);
        String toggleState = plugin.messages().raw(chatToggled ? "gui.toggle-on" : "gui.toggle-off");
        ItemStack chatToggle = new ItemBuilder(Material.WRITABLE_BOOK)
                .name(plugin.messages().text("guild.gui-toggle-chat-button", Placeholder.parsed("state", toggleState)))
                .lore(plugin.messages().text("gui.click-to-toggle"))
                .glow(chatToggled)
                .build();
        setButton(CHAT_TOGGLE_SLOT, chatToggle, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                boolean enabled = plugin.guildChat().toggle(player.getUniqueId());
                plugin.messages().send(player, enabled ? "guild.chat-enabled" : "guild.chat-disabled");
                new GuildMainGui(plugin, guildManager, guild, viewerUuid).open(player);
            }
        });

        if (viewerRole.canManageSettings()) {
            ItemStack setHomeIcon = new ItemBuilder(Material.COMPASS).name(plugin.messages().text("guild.gui-set-home-button")).build();
            setButton(SET_HOME_SLOT, setHomeIcon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    guildManager.setHome(guild.id(), player.getLocation()).thenRun(() -> plugin.messages().send(player, "guild.home-set"));
                }
            });

            ItemStack disbandIcon = new ItemBuilder(Material.TNT).name(plugin.messages().text("guild.gui-disband-button")).build();
            setButton(DISBAND_OR_LEAVE_SLOT, disbandIcon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    guildManager.disbandGuild(guild.id()).thenAccept(deleted -> {
                        if (deleted) {
                            plugin.messages().send(player, "guild.disbanded");
                        }
                    });
                    player.closeInventory();
                }
            });
        } else {
            ItemStack leaveIcon = new ItemBuilder(Material.OAK_DOOR).name(plugin.messages().text("guild.gui-leave-button")).build();
            setButton(DISBAND_OR_LEAVE_SLOT, leaveIcon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    guildManager.removeMember(guild.id(), player.getUniqueId()).thenRun(() ->
                            plugin.messages().send(player, "guild.left", Placeholder.unparsed("guild", guild.name())));
                    player.closeInventory();
                }
            });
        }

        ItemStack close = new ItemBuilder(Material.BARRIER).name(plugin.messages().text("gui.close")).build();
        setButton(CLOSE_SLOT, close, event -> event.getWhoClicked().closeInventory());

        fillBorder(plugin);
    }

    // Frames the content icons in glass so unused slots (e.g. set-home for non-owners) read as a panel rather than holes.
    private void fillBorder(OneSMPPlugin plugin) {
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(plugin.messages().text("gui.filler")).build();
        for (int slot = 0; slot < 27; slot++) {
            if (getInventory().getItem(slot) == null) {
                setItem(slot, filler);
            }
        }
    }
}
